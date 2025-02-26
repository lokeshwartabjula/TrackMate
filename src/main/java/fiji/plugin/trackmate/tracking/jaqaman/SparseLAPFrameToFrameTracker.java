/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2010 - 2023 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.tracking.jaqaman;

import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALTERNATIVE_LINKING_COST_FACTOR;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_FEATURE_PENALTIES;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.jaqaman.LAPUtils.checkFeatureMap;
import static fiji.plugin.trackmate.util.TMUtils.checkMapKeys;
import static fiji.plugin.trackmate.util.TMUtils.checkParameter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.scijava.Cancelable;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.tracking.SpotTracker;
import fiji.plugin.trackmate.tracking.jaqaman.costfunction.CostFunction;
import fiji.plugin.trackmate.tracking.jaqaman.costfunction.FeaturePenaltyCostFunction;
import fiji.plugin.trackmate.tracking.jaqaman.costfunction.SquareDistCostFunction;
import fiji.plugin.trackmate.tracking.jaqaman.costmatrix.JaqamanLinkingCostMatrixCreator;
import net.imglib2.algorithm.MultiThreadedBenchmarkAlgorithm;

public class SparseLAPFrameToFrameTracker extends MultiThreadedBenchmarkAlgorithm implements SpotTracker, Cancelable
{
	private final static String BASE_ERROR_MESSAGE = "[SparseLAPFrameToFrameTracker] ";

	protected SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;

	protected Logger logger = Logger.VOID_LOGGER;

	protected final SpotCollection spots;

	protected final Map< String, Object > settings;

	private boolean isCanceled;

	private String cancelReason;

	/*
	 * CONSTRUCTOR
	 */

	public SparseLAPFrameToFrameTracker( final SpotCollection spots, final Map< String, Object > settings )
	{
		this.spots = spots;
		this.settings = settings;
	}

	/*
	 * METHODS
	 */

	@Override
	public SimpleWeightedGraph< Spot, DefaultWeightedEdge > getResult()
	{
		return graph;
	}

	@Override
	public boolean checkInput()
	{
		return true;
	}

	@Override
	public boolean process()
	{
		isCanceled = false;
		cancelReason = null;

		/*
		 * Check input now.
		 */

		// Check that the objects list itself isn't null
		if ( null == spots )
		{
			errorMessage = BASE_ERROR_MESSAGE + "The spot collection is null.";
			return false;
		}

		// Check that the objects list contains inner collections.
		if ( spots.keySet().isEmpty() )
		{
			errorMessage = BASE_ERROR_MESSAGE + "The spot collection is empty.";
			return false;
		}

		// Check that at least one inner collection contains an object.
		boolean empty = true;
		for ( final int frame : spots.keySet() )
		{
			if ( spots.getNSpots( frame, true ) > 0 )
			{
				empty = false;
				break;
			}
		}
		if ( empty )
		{
			errorMessage = BASE_ERROR_MESSAGE + "The spot collection is empty.";
			return false;
		}
		// Check parameters
		final StringBuilder errorHolder = new StringBuilder();
		if ( !checkSettingsValidity( settings, errorHolder ) )
		{
			errorMessage = BASE_ERROR_MESSAGE + errorHolder.toString();
			return false;
		}

		/*
		 * Process.
		 */

		final long start = System.currentTimeMillis();

		// Prepare frame pairs in order, not necessarily separated by 1.
		final ArrayList< int[] > framePairs = new ArrayList<>( spots.keySet().size() - 1 );
		final Iterator< Integer > frameIterator = spots.keySet().iterator();
		int frame0 = frameIterator.next();
		int frame1;
		while ( frameIterator.hasNext() )
		{ // ascending order
			frame1 = frameIterator.next();
			framePairs.add( new int[] { frame0, frame1 } );
			frame0 = frame1;
		}

		// Prepare cost function
		@SuppressWarnings( "unchecked" )
		final Map< String, Double > featurePenalties = ( Map< String, Double > ) settings.get( KEY_LINKING_FEATURE_PENALTIES );
		final CostFunction< Spot, Spot > costFunction = getCostFunction( featurePenalties );
		final Double maxDist = ( Double ) settings.get( KEY_LINKING_MAX_DISTANCE );
		final double costThreshold = maxDist * maxDist;
		final double alternativeCostFactor = ( Double ) settings.get( KEY_ALTERNATIVE_LINKING_COST_FACTOR );

		// Instantiate graph
		graph = new SimpleWeightedGraph<>( DefaultWeightedEdge.class );

		// Prepare workers.
		final AtomicInteger progress = new AtomicInteger( 0 );
		final AtomicBoolean ok = new AtomicBoolean( true );
		final ExecutorService executors = Executors.newFixedThreadPool( numThreads );
		final List< Future< Void > > futures = new ArrayList<>( framePairs.size() );
		for ( final int[] framePair : framePairs )
		{
			final Future< Void > future = executors.submit( new Callable< Void >()
			{

				@Override
				public Void call() throws Exception
				{
					if ( !ok.get() || isCanceled() )
						return null;

					// Get frame pairs
					final int lFrame0 = framePair[ 0 ];
					final int lFrame1 = framePair[ 1 ];

					// Get spots - we have to create a list from each
					// content.
					final List< Spot > sources = new ArrayList<>( spots.getNSpots( lFrame0, true ) );
					for ( final Iterator< Spot > iterator = spots.iterator( lFrame0, true ); iterator.hasNext(); )
						sources.add( iterator.next() );

					final List< Spot > targets = new ArrayList<>( spots.getNSpots( lFrame1, true ) );
					for ( final Iterator< Spot > iterator = spots.iterator( lFrame1, true ); iterator.hasNext(); )
						targets.add( iterator.next() );

					if ( sources.isEmpty() || targets.isEmpty() )
						return null;

					/*
					 * Run the linker.
					 */

					final JaqamanLinkingCostMatrixCreator< Spot, Spot > creator = new JaqamanLinkingCostMatrixCreator<>( sources, targets, costFunction, costThreshold, alternativeCostFactor, 1d );
					final JaqamanLinker< Spot, Spot > linker = new JaqamanLinker<>( creator );
					if ( !linker.checkInput() || !linker.process() )
					{
						errorMessage = "At frame " + lFrame0 + " to " + lFrame1 + ": " + linker.getErrorMessage();
						ok.set( false );
						return null;
					}

					/*
					 * Update graph.
					 */

					synchronized ( graph )
					{
						final Map< Spot, Double > costs = linker.getAssignmentCosts();
						final Map< Spot, Spot > assignment = linker.getResult();
						for ( final Spot source : assignment.keySet() )
						{
							final double cost = costs.get( source );
							final Spot target = assignment.get( source );
							graph.addVertex( source );
							graph.addVertex( target );
							final DefaultWeightedEdge edge = graph.addEdge( source, target );
							graph.setEdgeWeight( edge, cost );
						}
					}

					logger.setProgress( progress.incrementAndGet() / framePairs.size() );
					return null;
				}
			} );
			futures.add( future );
		}

		logger.setStatus( "Frame to frame linking..." );
		try
		{
			for ( final Future< ? > future : futures )
				future.get();

			executors.shutdown();
		}
		catch ( InterruptedException | ExecutionException e )
		{
			ok.set( false );
			errorMessage = BASE_ERROR_MESSAGE + e.getMessage();
			e.printStackTrace();
		}
		logger.setProgress( 1. );
		logger.setStatus( "" );

		final long end = System.currentTimeMillis();
		processingTime = end - start;

		return ok.get();
	}

	/**
	 * Creates a suitable cost function.
	 *
	 * @param featurePenalties
	 *            feature penalties to base costs on. Can be <code>null</code>.
	 * @return a new {@link CostFunction}
	 */
	protected CostFunction< Spot, Spot > getCostFunction( final Map< String, Double > featurePenalties )
	{
		if ( null == featurePenalties || featurePenalties.isEmpty() )
			return new SquareDistCostFunction();

		return new FeaturePenaltyCostFunction( featurePenalties );
	}

	@Override
	public void setLogger( final Logger logger )
	{
		this.logger = logger;
	}

	protected boolean checkSettingsValidity( final Map< String, Object > settings, final StringBuilder str )
	{
		if ( null == settings )
		{
			str.append( "Settings map is null.\n" );
			return false;
		}

		boolean ok = true;
		// Linking
		ok = ok & checkParameter( settings, KEY_LINKING_MAX_DISTANCE, Double.class, str );
		ok = ok & checkFeatureMap( settings, KEY_LINKING_FEATURE_PENALTIES, str );
		// Others
		ok = ok & checkParameter( settings, KEY_ALTERNATIVE_LINKING_COST_FACTOR, Double.class, str );

		// Check keys
		final List< String > mandatoryKeys = new ArrayList<>();
		mandatoryKeys.add( KEY_LINKING_MAX_DISTANCE );
		mandatoryKeys.add( KEY_ALTERNATIVE_LINKING_COST_FACTOR );
		final List< String > optionalKeys = new ArrayList<>();
		optionalKeys.add( KEY_LINKING_FEATURE_PENALTIES );
		ok = ok & checkMapKeys( settings, mandatoryKeys, optionalKeys, str );

		return ok;
	}

	// --- org.scijava.Cancelable methods ---

	@Override
	public boolean isCanceled()
	{
		return isCanceled;
	}

	@Override
	public void cancel( final String reason )
	{
		isCanceled = true;
		cancelReason = reason;
	}

	@Override
	public String getCancelReason()
	{
		return cancelReason;
	}
}

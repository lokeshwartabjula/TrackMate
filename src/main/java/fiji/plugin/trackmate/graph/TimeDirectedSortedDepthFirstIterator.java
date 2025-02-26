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
/**
 *
 */
package fiji.plugin.trackmate.graph;

import fiji.plugin.trackmate.Spot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;

public class TimeDirectedSortedDepthFirstIterator extends SortedDepthFirstIterator< Spot, DefaultWeightedEdge >
{

	public TimeDirectedSortedDepthFirstIterator( final Graph< Spot, DefaultWeightedEdge > g, final Spot startVertex, final Comparator< Spot > comparator )
	{
		super( g, startVertex, comparator );
	}

	@Override
	protected void addUnseenChildrenOf( final Spot vertex )
	{

		// Retrieve target vertices, and sort them in a list
		final List< Spot > sortedChildren = new ArrayList<>();
		// Keep a map of matching edges so that we can retrieve them in the same
		// order
		final Map< Spot, DefaultWeightedEdge > localEdges = new HashMap<>();

		final int ts = vertex.getFeature( Spot.FRAME ).intValue();
		for ( final DefaultWeightedEdge edge : specifics.edgesOf( vertex ) )
		{

			final Spot oppositeV = Graphs.getOppositeVertex( graph, edge, vertex );
			final int tt = oppositeV.getFeature( Spot.FRAME ).intValue();
			if ( tt <= ts )
			{
				continue;
			}

			if ( !seen.containsKey( oppositeV ) )
			{
				sortedChildren.add( oppositeV );
			}
			localEdges.put( oppositeV, edge );
		}

		Collections.sort( sortedChildren, Collections.reverseOrder( comparator ) );
		final Iterator< Spot > it = sortedChildren.iterator();
		while ( it.hasNext() )
		{
			final Spot child = it.next();

			if ( nListeners != 0 )
			{
				fireEdgeTraversed( createEdgeTraversalEvent( localEdges.get( child ) ) );
			}

			if ( seen.containsKey( child ) )
			{
				encounterVertexAgain( child, localEdges.get( child ) );
			}
			else
			{
				encounterVertex( child, localEdges.get( child ) );
			}
		}
	}

}

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
package fiji.plugin.trackmate.visualization.trackscheme;

import static fiji.plugin.trackmate.visualization.trackscheme.TrackScheme.DEFAULT_CELL_HEIGHT;
import static fiji.plugin.trackmate.visualization.trackscheme.TrackScheme.DEFAULT_CELL_WIDTH;
import static fiji.plugin.trackmate.visualization.trackscheme.TrackScheme.DEFAULT_COLOR;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgrapht.graph.DefaultWeightedEdge;

import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxStyleUtils;
import com.mxgraph.view.mxPerimeter;
import com.mxgraph.view.mxStylesheet;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.features.FeatureUtils;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.visualization.FeatureColorGenerator;

public class TrackSchemeStylist
{
	private final Model model;

	private final DisplaySettings displaySettings;

	private final JGraphXAdapter graphx;

	private String globalStyle = DEFAULT_STYLE_NAME;

	private final Map< String, Object > simpleStyle;

	private final Map< String, Object > fullStyle;

	private final Map< String, Object > edgeStyle;

	static final List< String > VERTEX_STYLE_NAMES = new ArrayList<>();

	private static final String FULL_STYLE_NAME = "full";

	private static final String SIMPLE_STYLE_NAME = "simple";

	private static final String DEFAULT_STYLE_NAME = SIMPLE_STYLE_NAME;

	private static final Map< String, Object > FULL_VERTEX_STYLE = new HashMap<>();

	private static final Map< String, Object > SIMPLE_VERTEX_STYLE = new HashMap<>();

	private static final Map< String, Object > BASIC_EDGE_STYLE = new HashMap<>();

	static
	{
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_FILLCOLOR, "white" );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_FONTCOLOR, "black" );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_ALIGN, mxConstants.ALIGN_RIGHT );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_SHAPE, mxScaledLabelShape.SHAPE_NAME );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_IMAGE_ALIGN, mxConstants.ALIGN_LEFT );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_ROUNDED, true );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_PERIMETER, mxPerimeter.RectanglePerimeter );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_STROKECOLOR, DEFAULT_COLOR );
		FULL_VERTEX_STYLE.put( mxConstants.STYLE_NOLABEL, false );

		SIMPLE_VERTEX_STYLE.put( mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE );
		SIMPLE_VERTEX_STYLE.put( mxConstants.STYLE_NOLABEL, true );

		BASIC_EDGE_STYLE.put( mxConstants.STYLE_SHAPE, mxConstants.SHAPE_CONNECTOR );
		BASIC_EDGE_STYLE.put( mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER );
		BASIC_EDGE_STYLE.put( mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE );
		BASIC_EDGE_STYLE.put( mxConstants.STYLE_STARTARROW, mxConstants.NONE );
		BASIC_EDGE_STYLE.put( mxConstants.STYLE_ENDARROW, mxConstants.NONE );
		BASIC_EDGE_STYLE.put( mxConstants.STYLE_STROKECOLOR, DEFAULT_COLOR );
		BASIC_EDGE_STYLE.put( mxConstants.STYLE_NOLABEL, true );

		VERTEX_STYLE_NAMES.add( SIMPLE_STYLE_NAME );
		VERTEX_STYLE_NAMES.add( FULL_STYLE_NAME );
	}

	public TrackSchemeStylist( final Model model, final JGraphXAdapter graphx, final DisplaySettings displaySettings )
	{
		this.model = model;
		this.graphx = graphx;
		this.displaySettings = displaySettings;
		// Copy styles.
		this.simpleStyle = new HashMap<>( SIMPLE_VERTEX_STYLE );
		this.fullStyle = new HashMap<>( FULL_VERTEX_STYLE );
		this.edgeStyle = new HashMap<>( BASIC_EDGE_STYLE );

		// Prepare styles
		final mxStylesheet styleSheet = graphx.getStylesheet();
		styleSheet.setDefaultEdgeStyle( edgeStyle );
		styleSheet.setDefaultVertexStyle( simpleStyle );
		styleSheet.putCellStyle( FULL_STYLE_NAME, fullStyle );
		styleSheet.putCellStyle( SIMPLE_STYLE_NAME, simpleStyle );
	}

	public void setStyle( final String styleName )
	{
		if ( !graphx.getStylesheet().getStyles().containsKey( styleName ) )
			throw new IllegalArgumentException( "Unknown TrackScheme style: " + styleName );

		this.globalStyle = styleName;
	}

	/**
	 * Change the style of the edge cells to reflect the currently set color
	 * generator.
	 *
	 * @param edges
	 *            the {@link mxCell}s.
	 */
	public synchronized void updateEdgeStyle( final Collection< mxCell > edges )
	{
		final FeatureColorGenerator< DefaultWeightedEdge > trackColorGenerator = FeatureUtils.createTrackColorGenerator( model, displaySettings );
		final Color missingValueColor = displaySettings.getMissingValueColor();
		graphx.getModel().beginUpdate();
		try
		{
			for ( final mxCell cell : edges )
			{
				final DefaultWeightedEdge edge = graphx.getEdgeFor( cell );
				Color color = trackColorGenerator.color( edge );
				if ( color == null )
					color = missingValueColor;

				final String colorstr = Integer.toHexString( color.getRGB() ).substring( 2 );
				String style = cell.getStyle();
				style = mxStyleUtils.setStyle( style, mxConstants.STYLE_STROKECOLOR, colorstr );
				style = mxStyleUtils.setStyle( style, mxConstants.STYLE_STROKEWIDTH, Float.toString( ( float ) displaySettings.getLineThickness() ) );
				graphx.getModel().setStyle( cell, style );
			}
		}
		finally
		{
			graphx.getModel().endUpdate();
		}
	}

	public void updateVertexStyle( final Collection< mxCell > vertices )
	{
		final Font font = displaySettings.getFont();
		fullStyle.put( mxConstants.STYLE_FONTFAMILY, font.getFamily() );
		fullStyle.put( mxConstants.STYLE_FONTSIZE, "" + font.getSize() );
		fullStyle.put( mxConstants.STYLE_FONTSTYLE, "" + font.getStyle() );

		final FeatureColorGenerator< Spot > spotColorGenerator = FeatureUtils.createSpotColorGenerator( model, displaySettings );
		final Color missingValueColor = displaySettings.getMissingValueColor();

		graphx.getModel().beginUpdate();
		try
		{
			for ( final mxCell vertex : vertices )
			{
				final Spot spot = graphx.getSpotFor( vertex );
				if ( spot != null )
				{
					Color color = spotColorGenerator.color( spot );
					if ( color == null )
						color = missingValueColor;

					final String colorstr = Integer.toHexString( color.getRGB() ).substring( 2 );
					final String fillcolorstr = displaySettings.isTrackSchemeFillBox() ? colorstr : "white";
					setVertexStyle( vertex, colorstr, fillcolorstr );
				}
			}
		}
		finally
		{
			graphx.getModel().endUpdate();
		}
	}

	private void setVertexStyle( final mxICell vertex, final String colorstr, final String fillcolorstr )
	{
		String targetStyle = vertex.getStyle();
		targetStyle = mxStyleUtils.removeAllStylenames( targetStyle );
		targetStyle = mxStyleUtils.setStyle( targetStyle, mxConstants.STYLE_STROKECOLOR, colorstr );

		// Style specifics
		int width, height;
		if ( globalStyle.equals( SIMPLE_STYLE_NAME ) )
		{
			targetStyle = mxStyleUtils.setStyle( targetStyle, mxConstants.STYLE_FILLCOLOR, colorstr );
			width = DEFAULT_CELL_HEIGHT;
			height = width;
		}
		else
		{
			targetStyle = mxStyleUtils.setStyle( targetStyle, mxConstants.STYLE_FILLCOLOR, fillcolorstr );
			width = DEFAULT_CELL_WIDTH;
			height = DEFAULT_CELL_HEIGHT;
		}
		targetStyle = globalStyle + ";" + targetStyle;

		graphx.getModel().setStyle( vertex, targetStyle );
		graphx.getModel().getGeometry( vertex ).setWidth( width );
		graphx.getModel().getGeometry( vertex ).setHeight( height );
	}
}

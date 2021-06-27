/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2021 Fiji developers.
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
package fiji.plugin.trackmate.util;

import java.awt.Container;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;

import com.itextpdf.text.DocumentException;

import fiji.plugin.trackmate.features.ModelDataset;
import fiji.plugin.trackmate.gui.GuiUtils;
import ij.IJ;

public class ExportableChartPanel extends ChartPanel
{

	private static final long serialVersionUID = 1L;

	private static File currentDir;

	/*
	 * CONSTRUCTORS
	 */

	public ExportableChartPanel( final JFreeChart chart )
	{
		super( chart );
	}

	public ExportableChartPanel(
			final JFreeChart chart,
			final boolean properties,
			final boolean save,
			final boolean print,
			final boolean zoom,
			final boolean tooltips )
	{
		super( chart, properties, save, print, zoom, tooltips );
	}

	public ExportableChartPanel( final JFreeChart chart, final int width, final int height,
			final int minimumDrawWidth, final int minimumDrawHeight, final int maximumDrawWidth,
			final int maximumDrawHeight, final boolean useBuffer, final boolean properties,
			final boolean save, final boolean print, final boolean zoom, final boolean tooltips )
	{
		super( chart, width, height, minimumDrawWidth, minimumDrawHeight,
				maximumDrawWidth, maximumDrawHeight,
				useBuffer, properties, save, print, zoom, tooltips );
	}

	public ExportableChartPanel( final JFreeChart chart, final int width, final int height,
			final int minimumDrawWidth, final int minimumDrawHeight, final int maximumDrawWidth,
			final int maximumDrawHeight, final boolean useBuffer, final boolean properties,
			final boolean copy, final boolean save, final boolean print, final boolean zoom,
			final boolean tooltips )
	{
		super( chart, width, height, minimumDrawWidth, minimumDrawHeight,
				maximumDrawWidth, maximumDrawHeight,
				useBuffer, properties, copy, save, print, zoom, tooltips );
	}

	/*
	 * METHODS
	 */

	@Override
	protected JPopupMenu createPopupMenu( final boolean properties, final boolean copy, final boolean save, final boolean print, final boolean zoom )
	{
		final JPopupMenu menu = super.createPopupMenu( properties, copy, false, print, zoom );

		menu.addSeparator();

		final JMenuItem displayTableItem = new JMenuItem( "Display data tables" );
		displayTableItem.setActionCommand( "TABLES" );
		displayTableItem.addActionListener( e -> createDataTable() );
		menu.add( displayTableItem );

		final JMenuItem exportToFile = new JMenuItem( "Export plot to file" );
		exportToFile.addActionListener( e -> doSaveAs() );
		menu.add( exportToFile );

		return menu;
	}

	public void createDataTable()
	{
		XYPlot plot = null;
		try
		{
			plot = getChart().getXYPlot();
		}
		catch ( final ClassCastException e )
		{
			return;
		}

		final int nSets = plot.getDatasetCount();
		for ( int i = 0; i < nSets; i++ )
		{
			final ModelDataset dataset = ( ModelDataset ) plot.getDataset( i );
			final String xFeature = dataset.getXFeature();
			final String xStr = plot.getDomainAxis().getLabel();
			final String xFeatureName = labelFromStr( xStr );
			final String xUnits = unitsFromStr( xStr );
			final String tableTitle = plot.getChart().getTitle().getText();
			final String yUnits = unitsFromStr( plot.getRangeAxis().getLabel() );

			final ExportableChartValueTable table = new ExportableChartValueTable(
					dataset,
					xFeature,
					xFeatureName,
					xUnits,
					tableTitle,
					yUnits );
			GuiUtils.positionWindow( table, SwingUtilities.getWindowAncestor( this ) );
			table.setVisible( true );
		}
	}

	private static final String unitsFromStr( final String str )
	{
		final int i1 = str.lastIndexOf( '(' );
		final int i2 = str.lastIndexOf( ')' );
		if ( i1 >= 0 && i2 >= 0 && i2 > ( i1 + 1 ) )
			return str.substring( i1 + 1, i2 );
		if ( i2 == i1 + 1 )
			return "";
		return str;
	}

	private static final String labelFromStr( final String str )
	{
		final int i = str.indexOf( '(' );
		if ( i <= 0 )
			return str;
		if ( i > 1 )
			return str.substring( 0, i - 1 );
		return str.substring( 0, i );
	}

	/**
	 * Opens a file chooser and gives the user an opportunity to save the chart
	 * in PNG, PDF or SVG format.
	 */
	@Override
	public void doSaveAs()
	{
		if ( null == currentDir )
			currentDir = getDefaultDirectoryForSaveAs();

		final File file;
		if ( IJ.isMacintosh() )
		{
			Container dialogParent = getParent();
			while ( !( dialogParent instanceof Frame ) )
				dialogParent = dialogParent.getParent();

			final Frame frame = ( Frame ) dialogParent;
			final FileDialog dialog = new FileDialog( frame, "Export chart to PNG, PDF or SVG", FileDialog.SAVE );
			String defaultDir = null;
			if ( currentDir != null )
				defaultDir = currentDir.getPath();
			
			dialog.setDirectory( defaultDir );
			final FilenameFilter filter = new FilenameFilter()
			{
				@Override
				public boolean accept( final File dir, final String name )
				{
					return name.endsWith( ".png" ) || name.endsWith( ".pdf" ) || name.endsWith( ".svg" );
				}
			};
			dialog.setFilenameFilter( filter );
			dialog.setVisible( true );
			final String selectedFile = dialog.getFile();
			if ( null == selectedFile )
				return;

			file = new File( dialog.getDirectory(), selectedFile );
			currentDir = new File( dialog.getDirectory() );
		}
		else
		{
			final JFileChooser fileChooser = new JFileChooser();
			fileChooser.setCurrentDirectory( currentDir );
			fileChooser.addChoosableFileFilter( new FileFilter()
			{

				@Override
				public String getDescription()
				{
					return "PNG Image File";
				}

				@Override
				public boolean accept( final File f )
				{
					return f.getName().toLowerCase().endsWith( ".png" );
				}
			} );
			fileChooser.addChoosableFileFilter( new FileFilter()
			{

				@Override
				public String getDescription()
				{
					return "Portable Document File (PDF)";
				}

				@Override
				public boolean accept( final File f )
				{
					return f.getName().toLowerCase().endsWith( ".pdf" );
				}
			} );
			fileChooser.addChoosableFileFilter( new FileFilter()
			{

				@Override
				public String getDescription()
				{
					return "Scalable Vector Graphics (SVG)";
				}

				@Override
				public boolean accept( final File f )
				{
					return f.getName().toLowerCase().endsWith( ".svg" );
				}
			} );
			final int option = fileChooser.showSaveDialog( this );
			if ( option != JFileChooser.APPROVE_OPTION )
				return;

			file = fileChooser.getSelectedFile();
			currentDir = fileChooser.getCurrentDirectory();
		}

		if ( file.getPath().endsWith( ".png" ) )
		{
			try
			{
				ChartUtils.saveChartAsPNG( file, getChart(), getWidth(), getHeight() );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		else if ( file.getPath().endsWith( ".pdf" ) )
		{
			try
			{
				ChartExporter.exportChartAsPDF( getChart(), getBounds(), file );
			}
			catch ( final DocumentException | IOException e )
			{
				e.printStackTrace();
			}

		}
		else if ( file.getPath().endsWith( ".svg" ) )
		{
			try
			{
				ChartExporter.exportChartAsSVG( getChart(), getBounds(), file );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			IJ.error( "Invalid file extension.", "Please choose a filename with one of the 3 supported extension: .png, .pdf or .svg." );
		}
	}

}

/*
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
package fiji.plugin.trackmate.gui.displaysettings;

/**
 * A {@code double} variable that can take any value in a given range. A
 * {@link #setUpdateListener(UpdateListener) listener} is notified when the
 * value or its allowed range is changed.
 *
 * @author Tobias Pietzsch
 */
public class BoundedValueDouble
{
	private double rangeMin;

	private double rangeMax;

	private double currentValue;

	public interface UpdateListener
	{
		void update();
	}

	private UpdateListener updateListener;

	public BoundedValueDouble( final double rangeMin, final double rangeMax, final double currentValue )
	{
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.currentValue = currentValue;
		updateListener = null;
	}

	public double getRangeMin()
	{
		return rangeMin;
	}

	public double getRangeMax()
	{
		return rangeMax;
	}

	public double getCurrentValue()
	{
		return currentValue;
	}

	public void setRange( final double min, final double max )
	{
		assert min <= max;
		rangeMin = min;
		rangeMax = max;
		currentValue = Math.min( Math.max( currentValue, min ), max );

		if ( updateListener != null )
			updateListener.update();
	}

	public void setCurrentValue( final double value )
	{
		currentValue = value;

		if ( currentValue < rangeMin )
			currentValue = rangeMin;
		else if ( currentValue > rangeMax )
			currentValue = rangeMax;

		if ( updateListener != null )
			updateListener.update();
	}

	public void setUpdateListener( final UpdateListener l )
	{
		updateListener = l;
	}
}

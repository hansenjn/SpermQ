package spermQ.jnh.support;

import java.awt.Font;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

/**
These tools are free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

See the GNU General Public License for more details.

Copyright (C) 2016-2020 Jan N Hansen 
  
For any questions please feel free to contact me (jan.hansen@uni-bonn.de).

**/

public class constants {
	//Axes
	public final static double [] X_AXIS = {1.0,0.0,0.0};
	public final static double [] X_AXIS_2D = {1.0,0.0};
	public final static double [] Y_AXIS = {0.0,1.0,0.0};
	public final static double [] Y_AXIS_2D = {0.0,1.0};
	public final static double [] Z_AXIS = {0.0,0.0,1.0};
	
	//Decimal formats
	public static final DecimalFormat df6US = new DecimalFormat("#0.000000");
	public static final DecimalFormat df3US = new DecimalFormat("#0.000");
	public static final DecimalFormat df0 = new DecimalFormat("#0");
	public static final DecimalFormat df6GER = new DecimalFormat("#0,000000");
	public static final DecimalFormat df3GER = new DecimalFormat("#0,000");
	public static final DecimalFormat dfdialog = new DecimalFormat("#0.000000");
	
	//Date formats
	public static final SimpleDateFormat dateName = new SimpleDateFormat("yyMMdd_HHmmss");
	public static final SimpleDateFormat dateTab = new SimpleDateFormat("yyyy-MM-dd	HH:mm:ss");
	public static final SimpleDateFormat dateY = new SimpleDateFormat("yyyy");
	
	//Fonts
	public static final Font Head1 = new Font("Sansserif", Font.BOLD, 16);
	public static final Font Head2 = new Font("Sansserif", Font.BOLD, 14);
	public static final Font BoldTxt = new Font("Sansserif", Font.BOLD, 12);
	public static final Font PlTxt = new Font("Sansserif", Font.PLAIN, 12);
	
	//Constants
	public static final double sqrt2 = Math.sqrt(2.0);
	public static final double sqrt2d2 = sqrt2/2.0;
	public static final double halfPI = Math.PI/2.0;
}

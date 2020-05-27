/***===============================================================================
 SpermQ_.java Version 20190814
 
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 See the GNU General Public License for more details.
 
 Copyright (C) 2013 - 2019 Jan F Jikeli & Jan N Hansen 
   
 For any questions please feel free to contact me (jan.hansen@uni-bonn.de).
==============================================================================**/

package spermQ;

import ij.ImagePlus;
import ij.gui.Line;
import spermQ.jnh.support.*;

public class trackPoint2D {
	// properties
	private double cXp, cYp;
	private double pointIntensity;
	
	private double [] vector;
	private double [] normalVector;
	private double normalVectorLength;
	private double normalMaxIntensity;
	private double [] widthGaussParams;	//[][] gaussresults+4=sliceindex,resultnr
	
	private double relativeZ;
	private double meanIpZ, medianIpZ;
	
	private double arcLengthXYPos;
	private double [] orientedVectorBy2D;
	
	private double dAngle2D, curvature;
	private double [] dZ;
	
	public trackPoint2D(double px, double py){
		cXp = px;
		cYp = py;	
		
		vector = new double [] {0.0,0.0};
		normalVector = new double [] {0.0,0.0};
		normalVectorLength = 0.0;
		
		relativeZ = Double.NEGATIVE_INFINITY;	
		orientedVectorBy2D = new double [2];
				
		pointIntensity = 0.0;
		normalMaxIntensity = 0.0;
		
		widthGaussParams = new double [4];
		for(int j = 0; j < 4; j++){
			widthGaussParams [j] = Double.NEGATIVE_INFINITY;
		}
		
		dAngle2D = Double.NEGATIVE_INFINITY;
		dZ = new double [] {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		curvature = 0.0;
	}	
	
	//*********************
	//SETS
	//*********************	
	public void setVectors(double vx, double vy){
		//set normalized vector
			double vectorLength = Math.sqrt(Math.pow(vx,2.0)+Math.pow(vy,2.0));
			vector [0] = vx / vectorLength;
			vector [1] = vy / vectorLength;
			
		//define normal vector
			normalVector = tools.getNormalVectorXY(vector);
			normalVectorLength = tools.getVectorLength(normalVector);
	}
	
	public void widthGaussFit (int calculationRadius, double [] xNormalPoints, double [] yNormalPoints, double [] parameters, boolean correctCoords){
//		normalLineRadius = calculationRadius;
		widthGaussParams = new double [4];
		
		//y offset, height, center, width, slicePos
		double shift = parameters [2];
		relativeZ = parameters [3];
			
		//copy parameters and determine z
		for(int j = 0; j < 4; j++){
			widthGaussParams [j] = parameters [j];
		}
		if(correctCoords
				&& calculationRadius + 1 + (int)(shift/normalVectorLength) < xNormalPoints.length	//TODO put in 3D tool
				&& calculationRadius + 1 + (int)(shift/normalVectorLength) >= 0){					//TODO put in 3D tool
			this.correctCoordinates((shift/normalVectorLength) * normalVector[0], (shift/normalVectorLength) * normalVector[1]);
			parameters [2] = 0.0;
		}else{
			shift = 0.0;
		}
		
		//save interpolated point intensity and normal max intensity
		if(Math.abs((int)(shift/normalVectorLength) + (int)(shift/Math.abs(shift))) <= (calculationRadius - 1)){				
			pointIntensity = tools.getInterpolatedValue1D(shift, xNormalPoints[calculationRadius + 1 + (int)(shift/normalVectorLength)],
					xNormalPoints[calculationRadius + (int)(shift/normalVectorLength) + (int)(shift/Math.abs(shift))],
					yNormalPoints[calculationRadius + (int)(shift/normalVectorLength)],
					yNormalPoints[calculationRadius + (int)(shift/normalVectorLength) + (int)(shift/Math.abs(shift))]);
		}else if(Math.abs((int)(shift/normalVectorLength) - (int)(shift/Math.abs(shift))) <= (calculationRadius - 1)){
			pointIntensity = tools.getInterpolatedValue1D(shift, xNormalPoints[calculationRadius + 1 + (int)(shift/normalVectorLength)],
					xNormalPoints[calculationRadius + (int)(shift/normalVectorLength) - (int)(shift/Math.abs(shift))],
					yNormalPoints[calculationRadius + (int)(shift/normalVectorLength)],
					yNormalPoints[calculationRadius + (int)(shift/normalVectorLength) - (int)(shift/Math.abs(shift))]);
		}else{
			pointIntensity = yNormalPoints [calculationRadius];
		}
		
		normalMaxIntensity = tools.getMaximum(yNormalPoints);
	}
	
//	public void updateGaussParams(double [] parameters){
//		//copy parameters
//		for(int j = 0; j < 4; j++){
//			widthGaussParams [j] = parameters [j];
//		}
//	}
	
	private void correctCoordinates(double xShift, double yShift){
		cXp += xShift;
		cYp += yShift;
	}
	
	public void resetXCoordinate(double x){
		cXp = x;
	}
	
	public void resetYCoordinate(double y){
		cYp = y;
	}
		
	public void setInterpolatedZGauss4P (double interpolated, boolean median){
		if(median){
			medianIpZ = interpolated;
//			IJ.log("set " + medianInterpolatedZGauss4P);
		}else{
			meanIpZ = interpolated;
		}
	}
	
	public void setArcLengthPos(double pos){
		arcLengthXYPos = pos;
	}
	
	public void setOrientedVector2D(double [] origin, double angle){
		orientedVectorBy2D [0] = (this.getX() - origin [0]) * Math.cos(angle) + (this.getY() - origin [1]) * Math.sin(angle);
		orientedVectorBy2D [1] = -1 * (this.getX() - origin [0]) * Math.sin(angle) + (this.getY() - origin [1]) * Math.cos(angle);	
	}
			
	public void setDAngle2D (double a){
		dAngle2D = a;
	}
	
	public void setCurvatureFactor (double tensionFactor){
		curvature = tensionFactor;
	}

	public void setDZ (trackPoint2D refPoint){
		dZ [0] = this.getZ(tools2D.PUREZ) - refPoint.getZ(tools2D.PUREZ);
		dZ [1] = this.getZ(tools2D.MEANZ) - refPoint.getZ(tools2D.MEANZ);
		dZ [2] = this.getZ(tools2D.MEDIANZ) - refPoint.getZ(tools2D.MEDIANZ);
	}
	
	//*********************
	//GETS
	//*********************
		
	public double getX(){
		return cXp;
	}
	
	public double getY(){
		return cYp;		
	}
	
	public double getNormalVectorLength(){
		return normalVectorLength;
	}
	
	public double [] getNormalVector(){
		return normalVector;
	}
	
	public double [] getVector(){
		return vector;
	}
	
	public Line getVectorLine(ImagePlus imp){
		return new Line(cXp / imp.getCalibration().pixelWidth, cYp / imp.getCalibration().pixelHeight,
				(cXp / imp.getCalibration().pixelWidth) + vector [0], (cYp / imp.getCalibration().pixelHeight) + vector [1]);
	}
	
	public Line getNormalVectorLine(ImagePlus imp, double radius){
//		if(normalVectorLength==0.0) return null;
		return new Line(cXp / imp.getCalibration().pixelWidth + normalVector [0]*(radius), 		//x1
				cYp / imp.getCalibration().pixelHeight + normalVector [1]*(radius),				//y1
				cXp / imp.getCalibration().pixelWidth + (-1.0) * normalVector [0] * (radius), 	//x2
				cYp / imp.getCalibration().pixelHeight + (-1.0) * normalVector [1] * (radius));	//y2
	}
		
	public double getNormalMaxIntensity (){
		return normalMaxIntensity;
	}
		
	public double getNormalPointIntensity (){
		return pointIntensity;
	}
	
	public double getXYGaussWidth (){
		if(widthGaussParams [3] == Double.NEGATIVE_INFINITY){
			return 0.0;
		}
		return widthGaussParams [3];
	}
	
	public double getXYGaussCenter (){
		if(widthGaussParams [3] == Double.NEGATIVE_INFINITY){
			return 0.0;
		}
		return widthGaussParams [2];
	}
	
	public double [] getGaussParameters (){
		if(widthGaussParams [3] == Double.NEGATIVE_INFINITY){
			return new double [] {0.0, 0.0, 0.0, 0.0};
		}
		return widthGaussParams;
	}
	
	public double getArcLengthPos(){
		return arcLengthXYPos;	
	}
		
	public double getZ(int interpolation){
		if(interpolation == tools2D.NOZ){
			return 0.0;
		}else if(interpolation == tools2D.PUREZ && relativeZ != Double.NEGATIVE_INFINITY){
			return relativeZ;
		}else if(interpolation == tools2D.MEDIANZ){
//			if(medianInterpolatedZGauss4P==0.0)	IJ.log("returned medi " + medianInterpolatedZGauss4P);
			return medianIpZ;
		}else if(interpolation == tools2D.MEANZ){
			return meanIpZ;
		}
		return 0.0;
	}

	public boolean zDeterminable(){
		if(relativeZ != Double.NEGATIVE_INFINITY)	return true;
		return false;
	}
		
	public double [] get2DOrientedVector(){
		return orientedVectorBy2D;
	}
	
	public double getDAngle2D (){
		return dAngle2D;
	}
	
	public double getDZ (int encoding){
		return dZ [encoding-1];
	}
	
	public double getCurvatureFactor (){
		return curvature;
	}
}

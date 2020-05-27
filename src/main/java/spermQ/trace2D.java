/***===============================================================================
 SpermQ_.java Version 20190926
 
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import ij.IJ;
import ij.ImagePlus;
import spermQ.jnh.support.*;

public class trace2D {
	private ArrayList<trackPoint2D> points;
	private int frame;	// 0 <= frame <= imp.getNFrames()-1
	private double [][] orientationVector;
	private double [][] origin;
	private double [] theta;
	private double hrAngle;
	private double hrMaximumPosition;
	private double hrMaximumIntensity;
	private double [] hrMatrix;
	public boolean xyCorrected, relZ, linearInterpolated, hrVset, oriented;	
	
	
	public trace2D(ArrayList<trackPoint2D> pointList, int timeStep, double maxRefDist, ProgressDialog progress){
		pointList.trimToSize();
		points = new ArrayList<trackPoint2D>(pointList);
		this.frame = timeStep;
		
		orientationVector = new double [4][3];
		origin = new double [4][3];
		theta = new double [4];
		hrMatrix = new double [41];
		hrVset = false;
		oriented = false;
		xyCorrected = false;
		relZ = false;
		linearInterpolated = false;	
		
		this.updateAllOrientationVectors(maxRefDist, progress);
	}
	
	//copy function TODO
//	public trace (trace another) {
//		points = new ArrayList<trackPoint>(another.getTracePoints());
//		this.frame = another.frame;
//		
//		orientationVector = new double [4][3];
//		origin = new double [4][3];
//		theta = new double [4];
//		
//		g4pz = false;
//		linearInterpolated = false;	
//		
//		this.updateOrientationVector(15);
//	  }
	
//	public void setFrame(int t){ TODO
//		this.frame = t;
//	}
	
	public int getFrame(){
		return frame;
	}
	
	public ArrayList<trackPoint2D> getTracePoints(){
		return points;
	}
	
	public void reverseTracePoints(){
		Collections.reverse(points);		
	}
	
	public void upscalePoints(int factor){
		/**
		 * Add points between all adjacent points to increase preciseness of the trace
		 * factor: multiplication factor -> between two adjacent points (factor-1) points will be added
		 * */
		
		points.trimToSize();
		ArrayList<trackPoint2D> pointList = new ArrayList<trackPoint2D>(points.size()*factor); 
		for(int i = 0; i < points.size()-1; i++){
			trackPoint2D p = new trackPoint2D(points.get(i).getX(), points.get(i).getY());
			pointList.add(p);
			for(int j = 1; j < factor; j++){
				double fraction = (j)/(double)(factor);
				pointList.add(new trackPoint2D(tools.getInterpolatedValue1D(fraction, 0, 1, p.getX(), points.get(i+1).getX()),
						tools.getInterpolatedValue1D(fraction, 0, 1, p.getY(), points.get(i+1).getY())));
			}			
		}
		pointList.add(new trackPoint2D(points.get(points.size()-1).getX(), points.get(points.size()-1).getY()));
		
		//replace points array with new array
		points.clear();
		points.ensureCapacity(pointList.size());
		for(int i = 0; i < pointList.size(); i++){
			points.add(pointList.get(i));
		}
		points.trimToSize();
	}
	
	public void sortPointsAndRemoveOutliers(ProgressDialog progress, int type){
		//create sortedList (list)
		ArrayList<trackPoint2D> list = new ArrayList<trackPoint2D>(points.size());
		{
			list.add(points.get(0));
			points.remove(0);
			points.trimToSize();
			trackPoint2D end = points.get(points.size()-1);
			int outlierCount = 0;
			int index = 0;
			double distance;
			trackPoint2D p;
			int pIndex;
			ordering: while(!points.isEmpty()){
				distance = Double.POSITIVE_INFINITY;
				p = null;
				pIndex = -1;
				for(int i = points.size()-1; i >= 0; i--){
					if(tools2D.getDistance(points.get(i),list.get(index),type) < distance){
						p = points.get(i);
						pIndex = i;
						distance = tools2D.getDistance(points.get(i),list.get(index),type);
					}
				}
				//remove all points until pIndex;
				for(int i = pIndex; i >= 0; i--){
					points.remove(i);
					outlierCount++;
				}
				points.trimToSize();
				list.add(p);
				index++;
				if(p.equals(end)){	//TODO
					if(points.size()>0){
						progress.notifyMessage("t = " + frame + ": points resorted, " + outlierCount + " outliers removed...",  ProgressDialog.LOG);
					}					
//					IJ.log("Outliers removed in " + frame + ": "+ points.size());
					points.clear();					
					break ordering;
				}
			}
		}
		list.trimToSize();
		points = new ArrayList<trackPoint2D> (list);
		System.gc();
	}
	
	public void interpolateXYLinear(int plusMinusRange, int interpolationType){
		int nrOfPoints = points.size();
		
		//create point coordinate list
		double coords [][] = new double [nrOfPoints][2];
		for(int j = 0; j < nrOfPoints; j++){
			coords [j][0] = points.get(j).getX();
			coords [j][1] = points.get(j).getY();
		}
			
		int ctReqSize = (plusMinusRange)*(plusMinusRange)+1;
		
		double data [][] = new double [ctReqSize][3];	//x,y,distanceToPrevious
		double distances [] = new double [ctReqSize];
		int interPolCt;
		int index1, index2;
		for(int j = 1; j < nrOfPoints; j++){
			for(int m = 0; m < ctReqSize; m++){
				data [m][0] = Double.MAX_VALUE;
				data [m][1] = Double.MAX_VALUE;
				data [m][2] = Double.MAX_VALUE;
				distances [m] = Double.MAX_VALUE;
			}
			
			interPolCt = 0;
			data [interPolCt][0] = coords[j][0];
			data [interPolCt][1] = coords[j][1];
			data [interPolCt][2] = -1.0;
			distances [interPolCt] = data [interPolCt][2];
			interPolCt++;
			
			for(int d1 = -1; d1 >= -plusMinusRange; d1--){
				for(int d2 = 1; d2 <= plusMinusRange; d2++){
//					if(d1!=0&&d2!=0){
						if(j+d1 >= 0 && j+d1 < nrOfPoints
								&& j+d2 >= 0 && j+d2 < nrOfPoints){
							double [] newP = tools.projectPointToLine2D(coords[j][0],coords[j][1],
									coords[j+d1][0],coords[j+d1][1],
									coords[j+d2][0],coords[j+d2][1]);
//							if(Double.isNaN(newP [0]))	IJ.log("nP0 " + newP [0]);
//							if(Double.isNaN(newP [1]))	IJ.log("nP1 " + newP [1]);
//							
							data [interPolCt][0] = newP [0];
							data [interPolCt][1] = newP [1];
							//determine shift distance
							data [interPolCt][2] = tools2D.getDistance(new trackPoint2D(coords[j][0],coords[j][1]), new trackPoint2D(data[interPolCt][0],data[interPolCt][1]), tools2D.NOZ);				
							distances [interPolCt] = data [interPolCt][2];
							if(Double.isNaN(distances [interPolCt])){
								IJ.log("nP0 " + newP [0] + " nP1 " + newP [1]);
								IJ.log("p0 " + coords[j+d1][0] + " " + coords[j+d1][1]);
								IJ.log("p1 " + coords[j+d2][0] + " " + coords[j+d2][1]);
							}
							interPolCt++;
						}
//					}
				}
			}			
			
			if(interPolCt != 1){
//				if((interPolCt+1)!=ctReqSize)	IJ.log("p" + j + ": " + (interPolCt+1) + "-" + ctReqSize);
				
				Arrays.sort(distances);
				if(interpolationType == tools2D.MINDIST){
					index1 = tools.getIndexOfClosestValue(tools.getArrayColumn(data, 2), distances[1]);
					if(index1 >= 0){
						points.get(j).resetXCoordinate(data[index1][0]);
						points.get(j).resetYCoordinate(data[index1][1]);
					}else{
						IJ.log("index < 0" + " iPC=" +  interPolCt);
						String out = "";
						for(int m = 0; m < ctReqSize; m++){
							out += distances [m] + ";   ";
						}
						IJ.log(out);
					}
										
				}else if(interpolationType == tools2D.MAXDIST){
					index1 = tools.getIndexOfClosestValue(tools.getArrayColumn(data, 2), distances[interPolCt-1]);
					if(index1 >= 0){
						points.get(j).resetXCoordinate(data[index1][0]);
						points.get(j).resetYCoordinate(data[index1][1]);
					}else{
						IJ.log("index < 0");
						String out = "";
						for(int m = 0; m < ctReqSize; m++){
							out += distances [m] + ";   ";
						}
						IJ.log(out);
					}
					
				}else if(interpolationType == tools2D.MEAN){
					double x = 0.0, y = 0.0;
					for(int i = 0; i < interPolCt; i++){
						x += data [i][0];
						y += data [i][1];
					}
					
					points.get(j).resetXCoordinate(x/(double)(interPolCt));
					points.get(j).resetYCoordinate(y/(double)(interPolCt));
					
				}else if(interpolationType == tools2D.MEDIAN){	
//					double [] x = new double [interPolCt+1], y  = new double [interPolCt+1];
//					for(int i = 0; i <= interPolCt; i++){
////						if(data[i][0] == Double.MAX_VALUE) IJ.log("problem " + i);
////						if(data[i][1] == Double.MAX_VALUE) IJ.log("problem " + i);
//						x [i] = data [i][0];
//						y [i] = data [i][1];
//					}
//					Arrays.sort(x);
//					Arrays.sort(y);
//					
//					if((interPolCt+1)%2==0){
//						points.get(j).resetXCoordinate((x[(interPolCt)/2-1]+x[(interPolCt)/2])/2.0);
//						points.get(j).resetYCoordinate((y[(interPolCt)/2-1]+y[(interPolCt)/2])/2.0);
//					}else{
//						points.get(j).resetXCoordinate(x[(interPolCt)/2]);
//						points.get(j).resetYCoordinate(y[(interPolCt)/2]);
//					}
					
					if(interPolCt%2 == 0){
						index1 = tools.getIndexOfClosestValue(tools.getArrayColumn(data, 2), distances[(interPolCt)/2-1]);
						index2 = tools.getIndexOfClosestValue(tools.getArrayColumn(data, 2), distances[(interPolCt)/2]);
						
						if(index1 >= 0 && index2 >= 0){ 
							points.get(j).resetXCoordinate((data[index1][0]+data[index2][0])/2.0);
							points.get(j).resetYCoordinate((data[index1][1]+data[index2][1])/2.0);
						}else{
							IJ.log("index1/2 < 0");
							String out = "";
							for(int m = 0; m < ctReqSize; m++){
								out += distances [m] + ";   ";
							}
							IJ.log(out);
						}					
					}else{
						index1 = tools.getIndexOfClosestValue(tools.getArrayColumn(data, 2), distances[(interPolCt)/2]);
						
						if(index1 >= 0){
							points.get(j).resetXCoordinate(data[index1][0]);
							points.get(j).resetYCoordinate(data[index1][1]);
						}else{
							IJ.log("index < 0");
							String out = "";
							for(int m = 0; m < ctReqSize; m++){
								out += distances [m] + ";   ";
							}
							IJ.log(out);
						}					
					}				
				}else{
					IJ.error("wrong type of xy interpolation");
				}
			}
			
			
		}
	}
	
	public void updateAllOrientationVectors(double maxDistance, ProgressDialog progress){
		/**
		 * updates all origin and orientation vectors for which z-information is available
		 * maxDistance defines the maximum distance of points from the first point that are included into calculation
		 * */
		
		if(points.size()==0)	return;
				
//		if(relZ==false){
			this.updateOrientationVector(tools2D.NOZ, maxDistance, progress);
//		}else{
//			this.updateOrientationVector(tools2D.PUREZ, maxDistance);
//						
//			if(linearInterpolated){
//				this.updateOrientationVector(tools2D.MEDIANZ, maxDistance);
//				this.updateOrientationVector(tools2D.MEANZ, maxDistance);							
//			}
//		}				
	}
	
	/**
	 * updates origin and orientation vector of the trace according to the z-information selected in type
	 * @param types specifies which type of z-values shall be included
	 * @param maxDistance defines the maximum distance of points from the first point that are included into calculation
	 * */
	public void updateOrientationVector (int type, double maxDistance, ProgressDialog progress){
		origin [type][0] = points.get(0).getX();
		origin [type][1] = points.get(0).getY();
		origin [type][2] = points.get(0).getZ(type);
		
		
		orientationVector [type][0] = 0.0;
		orientationVector [type][1] = 0.0;
		orientationVector [type][2] = 0.0;
		
		int counter = 0, skippedPoints = 0;
		double divFactor;
		double [] newVector = {0.0,0.0,0.0};
		searching: while(true){
			if(points.size()==counter+1)	break searching;
			
			newVector [0] = points.get(counter+1).getX() - origin [type][0];
			newVector [1] = points.get(counter+1).getY() - origin [type][1];
			newVector [2] = points.get(counter+1).getZ(type) - origin [type][2];
//			if(Math.sqrt(Math.pow(xComponent, 2.0)+Math.pow(yComponent, 2.0)+Math.pow(zComponent, 2.0)) > maxDistance){
			if(tools.getVectorLength(newVector) > maxDistance){
				break searching;
			}else{
				newVector = tools.getNormalizedVector(newVector);
//				if(Double.isNaN(newVector [0]))	IJ.log("T" + frame + " newV0 NaN:! type " + type + " ct " + counter);
//				if(Double.isNaN(newVector [1]))	IJ.log("T" + frame + " newV1 NaN:! type " + type + " ct " + counter);
//				if(Double.isNaN(newVector [2]))	IJ.log("T" + frame + " newV2 NaN:! type " + type + " ct " + counter);
				
				if(!Double.isNaN(newVector [0]) && !Double.isNaN(newVector [1]) && !Double.isNaN(newVector [2])){
					divFactor = 1.0;					
					if(counter+2<points.size()){
						newVector [0] += points.get(counter+2).getX() - origin [type][0];
						newVector [1] += points.get(counter+2).getY() - origin [type][1];
						newVector [2] += points.get(counter+2).getZ(type) - origin [type][2];
						divFactor += 1.0;
					}					
					if(counter>0){
						newVector [0] += points.get(counter).getX() - origin [type][0];
						newVector [1] += points.get(counter).getY() - origin [type][1];
						newVector [2] += points.get(counter).getZ(type) - origin [type][2];
						divFactor += 1.0;
					}			
					
					orientationVector [type][0] = newVector [0] / divFactor;
					orientationVector [type][1] = newVector [1] / divFactor;
					orientationVector [type][2] = newVector [2] / divFactor;
				}else{
					skippedPoints++;
				}
				counter++;	
			}				
		}
		counter -= skippedPoints;
		if(counter!=0){
//			orientationVector [type][0] /= (double) counter;
//			orientationVector [type][1] /= (double) counter;
//			orientationVector [type][2] /= (double) counter;
			
			
//			if(Double.isNaN(orientationVector [type][0]))	IJ.log("T" + frame + " ov0 NaN: origin " + origin [type][0] + " - " + origin [type][1] + " - " + origin [type][2] + "! type " + type + " ct " + counter);
//			if(Double.isNaN(orientationVector [type][1]))	IJ.log("T" + frame + " ov1 NaN: origin " + origin [type][0] + " - " + origin [type][1] + " - " + origin [type][2] + "! type " + type + " ct " + counter);
//			if(Double.isNaN(orientationVector [type][2]))	IJ.log("T" + frame + " ov2 NaN: origin " + origin [type][0] + " - " + origin [type][1] + " - " + origin [type][2] + "! type " + type + " ct " + counter);
//			if(Double.isNaN(orientationVector [type][0]) || Double.isNaN(orientationVector [type][1]) || Double.isNaN(orientationVector [type][2])){
//				IJ.log("problem " + origin [type][0] + "-" + origin [type][1] + "-" + origin [type][2] + "! " + frame + " type" + type);
//			}
			if(orientationVector [type][0]==0.0
					&&orientationVector [type][1]==0.0
					&&orientationVector [type][2]==0.0){
				
			}
					
					
			theta [type] = tools.getAbsoluteAngle(orientationVector[type], constants.X_AXIS);
			oriented = true;
		}else{
			progress.notifyMessage("T"  + frame + " no orientation possible (0 points in proximity) - skipped: " + skippedPoints + " - pointCount " + points.size(), ProgressDialog.LOG);
//			IJ.log("T"  + frame + " no orientation possible (0 points in proximity) - skipped: " + skippedPoints + " - pointCount " + points.size());
		}
			
//		if(Double.isNaN(orientationVector [type][0]))	IJ.log("T" + frame + "ov0 NaN" + origin [type][0] + "-" + origin [type][1] + "-" + origin [type][2] + "! type" + type + " ct" + counter);
//		if(Double.isNaN(orientationVector [type][1]))	IJ.log("T" + frame + "ov1 NaN" + origin [type][0] + "-" + origin [type][1] + "-" + origin [type][2] + "! type" + type + " ct" + counter);
//		if(Double.isNaN(orientationVector [type][2]))	IJ.log("T" + frame + "ov2 NaN" + origin [type][0] + "-" + origin [type][1] + "-" + origin [type][2] + "! type" + type + " ct" + counter);
//		if(orientationVector [type][0]==0.0
//				&&orientationVector [type][1]==0.0
//				&&orientationVector [type][2]==0.0){
//			IJ.log("problem " + origin [type][0] + "-" + origin [type][1] + "-" + origin [type][2] + "! " + frame + " type" + type);
//		}
//		if(counter==0)IJ.log("problem ct=0 " + origin [type][0] + "-" + origin [type][1] + "-" + origin [type][2] + "! " + frame + " type" + type);
//		if(Double.isNaN(theta [type])){
//			IJ.log("theta NAN ");
//			IJ.log("  origin " + origin [type][0] + "-" + origin [type][1] + "-" + origin [type][2] + "! " + frame + " type" + type + " ct" + counter);
//			IJ.log("  ovec " + orientationVector [type][0] + "-" + orientationVector [type][1] + "-" + orientationVector [type][2] + "! " + frame + " type" + type + " ct" + counter);
//		}
	}
	
	
	
	public double [][] getOrientationVector (){
		return orientationVector;
	}
		
	public void calculateOrientedPointsAll2D(){		
		this.calculateOrientedPoints2D(tools2D.NOZ);
		if(relZ){
			this.calculateOrientedPoints2D(tools2D.PUREZ);
			if(linearInterpolated){
				this.calculateOrientedPoints2D(tools2D.MEANZ);
				this.calculateOrientedPoints2D(tools2D.MEDIANZ);
			}
		}
	}
	
	private void calculateOrientedPoints2D(int encoding){		
		for(int i = 0; i < points.size(); i++){
			points.get(i).setOrientedVector2D(origin [tools2D.NOZ], theta[tools2D.NOZ]);
		}
	}
		
	public void calculateDAnglesAndCurvatures(double upstreamDist, int preventPoints){
		double halfUpDist = upstreamDist / 2.0;
		trackPoint2D p = points.get(0), q = points.get(0);
		
		//calculate Tensions
		for(int j = 0; j < points.size(); j++){	
			searchUpstream: for(int vu = 0; vu <= j; vu++){
				p = points.get(j-vu);
				if(tools.mathAbs(p.getArcLengthPos()-points.get(j).getArcLengthPos())/halfUpDist>=1){
					break searchUpstream;
				}
			}			
			searchDownstream: for(int vu = 0; vu + j < points.size(); vu++){
				q = points.get(j+vu);
				if(tools.mathAbs(q.getArcLengthPos()-points.get(j).getArcLengthPos())/halfUpDist>=1){
					break searchDownstream;
				}
			}			
			points.get(j).setCurvatureFactor(tools2D.getCurvatureFactor(p, q));
		}
		
		//calculate dAngle
		p = points.get(0);
		q = points.get(0);
		double angle;
		for(int j = 0; j < points.size(); j++){
			searchUpstream: for(int vu = 0; vu <= j; vu++){
				p = points.get(j-vu);
				if(j-vu >= preventPoints) q = points.get(j-vu);
				
				if(tools.mathAbs(p.getArcLengthPos()-points.get(j).getArcLengthPos())/upstreamDist>=1){
					break searchUpstream;
				}
			}	
			angle = tools.get2DAngle(points.get(j).getVector(), p.getVector());
			if(angle > Math.PI) angle = angle - 2*Math.PI;
			points.get(j).setDAngle2D(Math.toDegrees(angle));
			points.get(j).setDZ(q);
		}
	}
	
	public void interpolateZLinear(double acceptedDist, int plusMinusPoints){
		int plusMinusPointsThird = (int)((double)plusMinusPoints/3.0);
		
		//count possible variants
		int ipVariants = 1;
		for(int d1 = -plusMinusPoints; d1 <= plusMinusPoints; d1++){
			for(int d2 = d1+1; d2 <= plusMinusPoints; d2++){
				ipVariants++;
			}
		}
		
		double meanZ;
		int interPolCounter;
		for(int j = 0; j < points.size(); j++){	
			double medianZ [] = new double [ipVariants];
			medianZ [0] = points.get(j).getZ(tools2D.PUREZ);
			for(int m = 1; m < ipVariants; m++){	
				medianZ [m] = Double.MAX_VALUE;
			}
			
			interPolCounter = 1;
			meanZ = medianZ [0];
			
			double distance1 = 0.0;
			double distance2 = 0.0;
			double value1 = 0.0;
			double value2 = 0.0;
			for(int d1 = -plusMinusPoints; d1 <= plusMinusPoints; d1++){
				for(int d2 = d1+1; d2 <= plusMinusPoints; d2++){
					if(d1!=0&&d2!=0){
						if(j+d1 > 0 && j + d1 < points.size()
								&& j + d2 > 0 && j + d2 < points.size()){
							if(tools.mathAbs(d2 - d1) > plusMinusPointsThird){
								distance1 = (tools.mathAbs(d1)/d1) * tools2D.getDistance(points.get(j+d1), points.get(j), tools2D.NOZ);
								distance2 = (tools.mathAbs(d2)/d2) * tools2D.getDistance(points.get(j+d2), points.get(j), tools2D.NOZ);
								
								value1 = points.get(j+d1).getZ(tools2D.PUREZ);								
								if(j+d1-1>=0){
									value1 += points.get(j+d1-1).getZ(tools2D.PUREZ);
									if(j+d1+1 < points.size()){
										value1 += points.get(j+d1+1).getZ(tools2D.PUREZ);
										value1 /= 3.0;
									}else{
										value1 /= 2.0;
									}
								}else if(j+d1+1 < points.size()){
									value1 += points.get(j+d1+1).getZ(tools2D.PUREZ);
									value1 /= 2.0;
								}
								
								value2 = points.get(j+d2).getZ(tools2D.PUREZ);
								if(j+d2-1>=0){
									value2 += points.get(j+d2-1).getZ(tools2D.PUREZ);
									if(j+d2+1 < points.size()){
										value2 += points.get(j+d2+1).getZ(tools2D.PUREZ);
										value2 /= 3.0;
									}else{
										value2 /= 2.0;
									}
								}else if(j+d2+1 < points.size()){
									value2 += points.get(j+d2+1).getZ(tools2D.PUREZ);
									value2 /= 2.0;
								}
								
								if(Math.abs(distance1) <= acceptedDist && Math.abs(distance2) <= acceptedDist
										&& tools.mathAbs(distance2-distance1) > 0.2){
									medianZ [interPolCounter] = tools.getInterpolatedValue1D(0.0,
											distance1, distance2, value1, value2);

									if(medianZ [interPolCounter] < -10.0 || medianZ [interPolCounter] > 20.0 ){	//TODO eventually needs adjustment
										medianZ [interPolCounter] = 0.0;
									}else{
										meanZ += medianZ [interPolCounter];
										interPolCounter++;
									}							
								}
							}
															
						}
					}
				}
			}
			meanZ /= (double) interPolCounter;	
			points.get(j).setInterpolatedZGauss4P(meanZ, false);
			if(interPolCounter!=1){
				Arrays.sort(medianZ);
				try{
					if((interPolCounter)%2==0){
						points.get(j).setInterpolatedZGauss4P((medianZ[(interPolCounter)/2-1]+medianZ[(interPolCounter)/2])/2.0, true);
					}else{
						points.get(j).setInterpolatedZGauss4P(medianZ[(int)((double)(interPolCounter)/2.0)], true);
					}
				}catch(Exception e){
					IJ.log("problem at t" +frame +" - ct="+ interPolCounter);
					IJ.log("j" + j);
				}
			}
		}				
		linearInterpolated = true;
	}
	
	public void removePoint(int index){
		points.remove(index);
		points.trimToSize();
	}
	
	public void trimList(){
		points.trimToSize();
	}
	
	public void calculateArcLengthPositions(boolean normalize, double targetArcLength){
		if(points.size()==0)	return;
		if(normalize){
			this.calculateNormalizedArcLengthPositions(targetArcLength);
		}else{
			double arcLength = 0.0;
			points.get(0).setArcLengthPos(0.0);
			for(int i = 1; i < points.size(); i++){
				arcLength += tools2D.getDistance(points.get(i),points.get(i-1),tools2D.NOZ);
				points.get(i).setArcLengthPos(arcLength);
			}
		}		
	}
	
	private void calculateNormalizedArcLengthPositions(double targetArcLength){
		double arcLength = 0.0;
		double dist;
		double maxAl = this.getArcLengthXY();		
		points.get(0).setArcLengthPos(0.0);
		for(int i = 1; i < points.size(); i++){
			dist = (tools2D.getDistance(points.get(i),points.get(i-1),tools2D.NOZ) / maxAl) * targetArcLength;
			arcLength += dist;
			points.get(i).setArcLengthPos(arcLength);			
		}
	}
	
	public void calculateHeadRotationAngle2D(ImagePlus imp, int plusMinusRange, ProgressDialog progress){
		this.hrMatrix = new double [plusMinusRange*2+1];
		boolean completeDetermination = true;
		{
			double [] normalOV = tools.getNormalVectorXY(this.orientationVector[tools2D.NOZ]);
			normalOV = tools.getNormalizedVector(normalOV);
			double [] OV = tools.getNormalizedVector(this.orientationVector[tools2D.NOZ]);
			
			double intensity = 0.0;
			double point [] = new double [] {0.0,0.0};
						
			{
				int counter = 0;
				for(int i = -plusMinusRange; i <= plusMinusRange; i++){
					counter = 0;
					for(int j = -4; j <= 4; j++){
						try{
							//TODO normal correction removed
//							point [0] = normalVector [0] * normalCorrection + this.origin [tools2D.NOZ][0] + normalOV [0] * i * imp.getCalibration().pixelWidth;
//							point [1] = normalVector [1] * normalCorrection + this.origin [tools2D.NOZ][1] + normalOV [1] * i * imp.getCalibration().pixelHeight;
							
							point [0] = this.origin [tools2D.NOZ][0] + normalOV [0] * i * imp.getCalibration().pixelWidth;
							point [1] = this.origin [tools2D.NOZ][1] + normalOV [1] * i * imp.getCalibration().pixelHeight;
							
							intensity = impProcessing.getInterpolatedIntensity2D(imp,
									point [0] + OV [0] * j * imp.getCalibration().pixelWidth,
									point [1] + OV [1] * j * imp.getCalibration().pixelHeight,
									imp.getStackIndex(1, 1, frame+1)-1);
																	
							hrMatrix [plusMinusRange+i] += intensity;
						}catch(Exception e){
							completeDetermination = false;
							continue;
						}	
						counter++;
					}
					hrMatrix [plusMinusRange+i] /= counter;
					if(counter!=9){
						progress.notifyMessage("Trace " + this.frame + " - range " + (plusMinusRange+i) + ": reduced hrRange (<9) = " + counter, ProgressDialog.LOG);
					}
				}
				if(tools.getMaximumIndex(hrMatrix) >= 0){
					hrMaximumPosition = (tools.getMaximumIndex(hrMatrix) - plusMinusRange) * imp.getCalibration().pixelWidth;
					hrMaximumIntensity = hrMatrix [tools.getMaximumIndex(hrMatrix)];
				}else{
					completeDetermination = false;
				}
			}			
		}
		System.gc();
		if(completeDetermination)	hrVset = true;
	}
		
	public double getHeadRotationAngle (double minIntensity, double maxIntensity, double minDisplacement, double maxDisplacement){
		double [] vector = {0.0,0.0};
		vector [0] = tools.getNormalizedValue(hrMaximumPosition, minDisplacement, maxDisplacement) - 0.5;
		vector [1] = tools.getNormalizedValue(hrMaximumIntensity, minIntensity, maxIntensity) - 0.5;
		hrAngle = Math.toDegrees(tools.get2DAngle(vector, constants.X_AXIS_2D));
		return hrAngle;
	}
				
	public double getHeadRotationMaximumPositions(){
		if(!hrVset){
			return 0.0;
		}		
		return hrMaximumPosition;
	}
	
	public double getHeadRotationMaximumIntensities(){
		if(!hrVset){
			return 0.0;
		}		
		return hrMaximumIntensity;
	}
		
	public double [] getHeadRotationMatrix(){
		if(!hrVset){
			IJ.error("head rotation vector set not initialized");
		}		
		return hrMatrix;
	}
	
	public double getArcLengthXY(){
		double length = 0.0;
		for(int i = 1; i < points.size(); i++){
			length += tools2D.getDistance(points.get(i), points.get(i-1),tools2D.NOZ);
		}
		return length;
	}
	
	public double getArcLengthXZ(int type){
		double length = 0.0;
		for(int i = 1; i < points.size(); i++){
			length += Math.sqrt(Math.pow(points.get(i).getX()-points.get(i-1).getX(), 2.0)
					+ Math.pow(points.get(i).getZ(type)-points.get(i-1).getZ(type), 2.0));
		}	
		return length;
	}
	
	public double getArcLengthYZ(int type){
		double length = 0.0;
		for(int i = 1; i < points.size(); i++){
			length += Math.sqrt(Math.pow(points.get(i).getY()-points.get(i-1).getY(), 2.0)
					+ Math.pow(points.get(i).getZ(type)-points.get(i-1).getZ(type), 2.0));
		}
		return length;
	}
	
	public double getArcLength3D(int type){
		double length = 0.0;
		for(int i = 1; i < points.size(); i++){
			length += tools2D.getDistance(points.get(i),points.get(i-1),type);
		}
		return length;
	}	
	
	public double getMinWidth(){
		double minWidth = Double.POSITIVE_INFINITY;
		for(int i = 0; i < points.size(); i++){
			if(points.get(i).getXYGaussWidth() < minWidth)	minWidth = points.get(i).getXYGaussWidth();
		}
		return minWidth;
	}
	
	public double getMaxWidth(){
		double maxWidth = 0.0;
		for(int i = 0; i < points.size(); i++){
			if(points.get(i).getXYGaussWidth() > maxWidth)	maxWidth = points.get(i).getXYGaussWidth();
		}
		return maxWidth;
	}
	
	public double getThetaDegree(int type){
		if(Double.isNaN(Math.toDegrees(theta[type]))){
			IJ.log("t=" + frame + ": in degree = " + Math.toDegrees(theta[type]) + " - orth " + theta[type]);
		}
		return Math.toDegrees(theta[type]);
//		return theta[type];
	}
}


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
 
 Copyright (C) 2016 - 2019: Jan N Hansen and Jan F Jikeli
   
 For any questions please feel free to contact me (jan.hansen@uni-bonn.de).

==============================================================================**/
package spermQ;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.process.*;
import ij.process.AutoThresholder.Method;
import ij.text.TextPanel;
import ij.measure.*;
import ij.plugin.frame.RoiManager;
import spermQ.edu.emory.mathcs.jtransforms.fft.*;
import spermQ.jnh.support.*;
import spermQ.skeleton_analysis.*;
import spermQ.skeletonize3D.Skeletonize3D_;
public class tools2D implements Measurements{
	//encodings
	public static final int NOZ = 0,
			PUREZ = 1,
			MEANZ = 2,
			MEDIANZ = 3;
	
	//interpolations
	public static final int MINDIST = 0,
			MAXDIST = 1,
			MEAN = 2,
			MEDIAN = 3;
	
	//Kymograph types
	public static final int KYMOX = 0,
			KYMOY = 1,
			KYMOZ = 2,
			KYMOMAXINTENSITY = 3,
			KYMOCURV = 4,
			KYMOCANGLEXY = 5,
			KYMODZ = 6,
			KYMOTANGENTANGLE = 7,
			KYMOXRAW = 8,
			KYMOYRAW = 9;
	
	//Kymograph minima and maxima
	public static final double KYMIN_X = -50.0, KYMAX_X = 130.0,
			KYMIN_Y = -130.0, KYMAX_Y = 130.0,
			KYMIN_Z = 0.0, KYMAX_Z = 15.0,
			KYMIN_MAXINTENSITY = 1.0, KYMAX_MAXINTENSITY = 65533.0,
			KYMIN_CURV = -0.2, KYMAX_CURV = 0.2,
			KYMIN_DANGLEXY = -180.0, KYMAX_DANGLEXY = 180.0,
			KYMIN_DZ = -15.0, KYMAX_DZ = 15.0,
			KYMIN_FREQ = 1.0, KYMAX_FREQ = 200.0,
			KYMIN_AMPLITUDE = 0.0, KYMAX_AMPLITUDE = 65534.0,
			KYMIN_AMPLITUDESMALLPARAM = 0.0, KYMAX_AMPLITUDESMALLPARAM = 1000.0;
	
	public static final int TRACE_THETA = 0,
			TRACE_HRMAXINTENSITY = 1,
			TRACE_HRMAXPOSITION = 2,
			TRACE_HRANGLE = 3;
	
	public static final double SPECIESLENGTH_HUMAN = 40,
			SPECIESLENGTH_MOUSE = 100,
			CILIALENGTH_BRAIN_ZEBRAFISH = 20;
	
	public static final String FREQUENCYPARAMETERASSTRING [] = {"primary frequency peak (Hz)", "height of primary frequency peak", "secondary frequency peak (Hz)", "height of secondary frequency peak", "COM frequency (Hz)"};
	
	public static Roi getSelection(ImagePlus imp){
		ImagePlus maxImp = impProcessing.maxIntensityProjection(imp);
		maxImp.show();
		IJ.setTool("polygon");
		while(true){
			new WaitForUserDialog("Set a Roi containing parts of the cell in every frame!").show();
			if(maxImp.getRoi()!=null) break;
		}		
		Roi roi = new PolygonRoi(maxImp.getRoi().getPolygon(), PolygonRoi.POLYGON);
		maxImp.changes = false;
		maxImp.close();
		
		return roi;
	}
	
	public static Roi getSelectionInStack(ImagePlus imp){
		imp.show();
		IJ.setTool("polygon");
		while(true){
			new WaitForUserDialog("Set a Roi circumscribing the Roi to measure SD!").show();
			if(imp.getRoi()!=null) break;
		}		
		Roi roi = new PolygonRoi(imp.getRoi().getPolygon(), PolygonRoi.POLYGON);
		
		imp.hide();
		
		return roi;
	}
	
	/**
	 * @return an independent ImagePlus of the sharpest image in the ImagePlus within the time-frame range
	 * The sharpest image is defined as the image with the lowest Standard Deviation 
	 * */
	public static ImagePlus getSharpestPlane(ImagePlus imp, double [] sliceDistances, double stepDistance, int rangeMin, int rangeMax){
		double [] SD = new double [imp.getStackSize()];
		double maximum = 0.0; int maximumPos = -1;
		
		int z, counter; 
		double average;
		int newRangeMin, newRangeMax;
		for(int s = 0; s < 4; s++){
			newRangeMin = rangeMin + (int)Math.round((sliceDistances [s] - sliceDistances [0]) / stepDistance);
			if(newRangeMin < 0)	newRangeMin = 0;
			newRangeMax = rangeMin + (int)Math.round((sliceDistances [s] - sliceDistances [0]) / stepDistance);
			
			for(int t = newRangeMin; t <= newRangeMax && t <= imp.getNFrames(); t++){
				z = imp.getStackIndex(1, s+1, t+1);
				average = 0.0;
				counter = 0;
				for(int x = 0; x < imp.getWidth(); x++){
					for(int y = 0; y < imp.getHeight(); y++){
						if(imp.getStack().getVoxel(x, y, z)!=0.0){
							average += imp.getStack().getVoxel(x, y, z);
							counter++;
						}
					}
				}
				average /= counter;
				
				for(int x = 0; x < imp.getWidth(); x++){
					for(int y = 0; y < imp.getHeight(); y++){
						if(imp.getStack().getVoxel(x, y, z)!=0.0){
							SD [z] += Math.pow(imp.getStack().getVoxel(x, y, z) - average, 2.0);
						}					
					}
				}
				SD [z] = Math.sqrt(SD [z] / (double)(counter-1));
				if(SD[z] > maximum) {
					maximum = SD [z]; 
					maximumPos = z;			
				}
			}		
		}	
		
		return impProcessing.getSingleImageFromStack(imp, maximumPos);
	}
	
	/**
	 * @return an independent ImagePlus of the selected Timepoint
	 * @param imp: image where single timepoint shall be derived from
	 * @param t: selected timestep 0 < t < #time-steps in image)
	 * */
	public static ImagePlus getSingleTimepoint(ImagePlus imp, int t){
		ImagePlus newImp = IJ.createHyperStack("T" + constants.df0.format(t), imp.getWidth(), imp.getHeight(), 
				imp.getNChannels(), imp.getNSlices(), 1, imp.getBitDepth());
		for(int c = 0; c < imp.getNChannels(); c++){
			for(int s = 0; s < imp.getNSlices(); s++){
				for(int x = 0; x < imp.getWidth(); x++){
					for(int y = 0; y < imp.getHeight(); y++){
						newImp.getStack().setVoxel(x, y, newImp.getStackIndex(c+1,s+1,1)-1, 
								imp.getStack().getVoxel(x, y, imp.getStackIndex(c+1, s+1, t+1)-1));
					}
				}
			}
		}			
		newImp.setCalibration(imp.getCalibration());
		return newImp;
	}
	
	public static ArrayList<trace2D> getObjectTraces(ImagePlus imp, String algorithm, double sigma, ProgressDialog progress, Roi selection, double maxRefDist, 
			boolean repeatGauss, boolean blurSelectionOnly){
		/**
		 * Generates a trace object out of an image
		 * algorithm: selected threshold method (ImageJ standard thresholding methods)
		 * sigma: Gauss-filter sigma
		 * progress: Dialog, where notifications and progress shall be depicted
		 * selection: Only skeleton objects inside this Roi will be accepted for determining the trace object
		 * calibrationMode: In the calibrationMode the trace object is generated in the sharpest stack image and is used in all images!
		 * */
		
		//not implemented for multichannel so far... 
		if(imp.getNChannels()!=1) return null;
		
		ArrayList<trace2D> traceList = new ArrayList<trace2D>(imp.getNFrames());
				
		// analysis mode
		ImagePlus selImp;
		for(int t = 0; t < imp.getNFrames(); t++){
			if(progress.isStopped())	return null;
			progress.addToBar(0.1*(1.0/(double)imp.getNFrames()));
			progress.updateBarText("generate trace for t=" + (t+1) + "...");
			
			selImp = getSingleTimepoint(imp,t);
			try{
				trace2D trace = getTraceBySkeletonization(selImp, t, selection, sigma, algorithm, maxRefDist, repeatGauss, blurSelectionOnly, progress);
				trace.trimList();
				if(trace != null){
					traceList.add(trace);
				}
			}catch(Exception e){
				String out = "";
				for(int err = 0; err < e.getStackTrace().length; err++){
					out += " \n " + e.getStackTrace()[err].toString();
				}
				progress.notifyMessage("An error occured during trace generation for t=" + (t+1) + ". This time-point was skipped. Error: " + out, ProgressDialog.LOG);
			}
		}
		System.gc();
		return traceList;
	}
	
	private static trace2D getTraceBySkeletonization (ImagePlus impIn, int frame, Roi selection, double sigma, String thresholdAlgorithm,
			double maxRefDist, boolean repeatGauss, boolean blurSelectionOnly, ProgressDialog progress){	
		ImagePlus impMaxSave = impIn.duplicate();
		
		//eventually scale down before thresholding?	TODO
					
		//gauss-filter
			if(blurSelectionOnly){
				ImagePlus impIn2 = impIn.duplicate();
				impIn2.getProcessor().blurGaussian(sigma);			
				for(int x = 0; x < impIn2.getWidth(); x++){
					for(int y = 0; y < impIn2.getHeight(); y++){
						if(selection.contains(x,y)){
							impIn.getStack().setVoxel(x,y,0,impIn2.getStack().getVoxel(x,y,0));
						}
					}
				}
				impIn2.changes = false;
				impIn2.close();
			}else{
				impIn.getProcessor().blurGaussian(sigma);
			}
			System.gc();
//			IJ.log("bl1"); tools.userCheckImage(impMax);
						
		//threshold image
			thresholdImage(impIn,thresholdAlgorithm);
//			ImagePlus impMaxSaveThresholded = impIn.duplicate();
			
		//gauss-filter
			if(repeatGauss){
				if(blurSelectionOnly){
					ImagePlus impMax2 = impIn.duplicate();
					impMax2.getProcessor().blurGaussian(sigma);			
					for(int x = 0; x < impMax2.getWidth(); x++){
						for(int y = 0; y < impMax2.getHeight(); y++){
							if(selection.contains(x,y)){
								impIn.getStack().setVoxel(x,y,0,impMax2.getStack().getVoxel(x,y,0));
							}
						}
					}
					impMax2.changes = false;
					impMax2.close();
				}else{
					impIn.getProcessor().blurGaussian(sigma);
				}
//				IJ.log("blur2");userCheck(impMax);
			}
			
		//convert to 8-bit
			impProcessing.optimal8BitConversion(impIn);
					
		//skeletonize
			double pixelWidth = impIn.getCalibration().pixelWidth;
			double pixelHeight = impIn.getCalibration().pixelHeight;
			
			impIn.getCalibration().pixelWidth = 1.0;
			impIn.getCalibration().pixelHeight = 1.0;
			
//			IJ.run(impMax,"Skeletonize (2D/3D)","");
			Skeletonize3D_ skelProc = new Skeletonize3D_();
			skelProc.setup("", impIn);
			skelProc.run(impIn.getProcessor());
//			IJ.log("skeletonized"); userCheck(impMax); 
//			IJ.log("ph " + impMax.getCalibration().pixelHeight);
		
		//analyze skeleton for shortest path
			AnalyzeSkeleton_ skel = new AnalyzeSkeleton_();
			skel.calculateShortestPath = true;
			skel.setup("", impIn);
			
			SkeletonResult sklRes = skel.run(AnalyzeSkeleton_.NONE, false, true, null, true, false);
			//run(int pruneIndex, boolean pruneEnds, boolean shortPath, ImagePlus origIP, boolean silent, boolean verbose)
			ArrayList<spermQ.skeleton_analysis.Point>[] shortestPath = skel.getShortestPathPoints();
				
		//find longest Skeleton touching selection
			int chosenShortestPath = 0;
			if(shortestPath.length==0){
				return null;
			}else if(shortestPath.length!=1){
				double maxLength = 0.0;
				for(int i = 0; i < sklRes.getNumOfTrees(); i++){
					checking: for(int j = 0; j < shortestPath[i].size(); j++){
						if(selection.contains(shortestPath[i].get(j).x, shortestPath[i].get(j).y)){
							if(sklRes.getAverageBranchLength()[i]*sklRes.getBranches()[i]>maxLength){
								maxLength = sklRes.getAverageBranchLength()[i]*sklRes.getBranches()[i];
								chosenShortestPath = i;
							}
							break checking;
						}							
					}											
				}
			}		
			
		//count points 
			int nPoints = shortestPath[chosenShortestPath].size();
//			IJ.log("n" + nPoints);
			
		//get trace list -  new method
			ArrayList<trackPoint2D> list = new ArrayList<trackPoint2D>(nPoints);
			LinkedList<trackPoint2D> unsortedList = new LinkedList<trackPoint2D>();
							
			//find end point
//			trackPoint2D startEnd = null; int startIndex = -1;
			LinkedList<trackPoint2D> startEnds = new LinkedList <trackPoint2D>();
			LinkedList<Integer> startIndexes = new LinkedList <Integer>();
			
			int counter;
			trackPoint2D p, q;
			searching: for(int i = 0; i < nPoints; i++){
				counter = 0;
				p = new trackPoint2D(shortestPath[chosenShortestPath].get(i).x,
						shortestPath[chosenShortestPath].get(i).y);
				searchingSecond: for(int j = 0; j < nPoints; j++){
					if(i!=j){
						q = new trackPoint2D(shortestPath[chosenShortestPath].get(j).x,
								shortestPath[chosenShortestPath].get(j).y);
//						IJ.log(i + " - " + j + " = " + get2DDistance(p,q));
						if(getDistance(p,q,NOZ) <= constants.sqrt2){
//							IJ.log(i + "-" + j);
							counter++;
							if(counter==2){
								break searchingSecond;
							}
						}
					}					
				}
				if (counter==1){ 
//					IJ.log("found start" + i);
//					IJ.log("counter " + counter);
					startEnds.add(new trackPoint2D(shortestPath[chosenShortestPath].get(i).x * pixelWidth,
							shortestPath[chosenShortestPath].get(i).y * pixelHeight));
					startIndexes.add(i);
//					break searching;
				}								
			}
			System.gc();
			
			if(startIndexes.isEmpty()){//Stop checking!
				progress.notifyMessage("frame " + frame + ": no start found", ProgressDialog.NOTIFICATION);
				return null;
			}
			
			//obtain best point list
			int minLeftOverPoints = Integer.MAX_VALUE, bestIndex = -2; 
			boolean isIn;
			findingBest: for(int se = 0; se < startEnds.size(); se++){
				ArrayList<trackPoint2D> tempList = new ArrayList<trackPoint2D>(nPoints);
				//save unsorted list
				for(int i = 0; i < nPoints; i++){
					if(i != startIndexes.get(se)){
						unsortedList.add(new trackPoint2D(shortestPath[chosenShortestPath].get(i).x*pixelWidth,
								shortestPath[chosenShortestPath].get(i).y*pixelHeight));
					}					
				}
				
				//create sortedList (list)
				{
					tempList.add(startEnds.get(se));
					
//					IJ.log(unsortedList.size() + " uls");
//					IJ.log(list.size() + " ls");
					
					int index = 0;
					double distance;
					int pIndex;
					
					sorting: while(!unsortedList.isEmpty()){
						distance = Double.POSITIVE_INFINITY; 
						p = null;
						pIndex = -1;
						for(int i = 0; i < unsortedList.size(); i++){
							if(getDistance(unsortedList.get(i),tempList.get(index),NOZ) < distance){
								p = unsortedList.get(i);
								pIndex = i;
								distance = getDistance(unsortedList.get(i),tempList.get(index),NOZ);
							}
						}
						if(p.equals(null)){
							IJ.log("Problem no next point found");
						}					
						unsortedList.remove(pIndex);						
						if(Math.sqrt(Math.pow((int)Math.round(p.getX()/pixelWidth)-(int)Math.round(tempList.get(index).getX()/pixelWidth),2.0)+
								Math.pow((int)Math.round(p.getY()/pixelHeight)-(int)Math.round(tempList.get(index).getY()/pixelHeight),2.0)) > constants.sqrt2){
							isIn = false;
							scanJnctn: for(int jnctn = 0; jnctn < sklRes.getListOfJunctionVoxels().size(); jnctn++){
								if(sklRes.getListOfJunctionVoxels().get(jnctn).x == (int)Math.round(tempList.get(index).getX()/pixelWidth) 
										&& sklRes.getListOfJunctionVoxels().get(jnctn).y == (int)Math.round(tempList.get(index).getY()/pixelWidth)){
									isIn = true;
									break scanJnctn;
								}
							}
							if(!isIn){
//								progress.notifyMessage("frame " + frame + " - try " + (se+1) + "/" + startEnds.size() + ": " + unsortedList.size() + " points discarded. X " 
//										+ (int)Math.round(p.getX()/pixelWidth)
//										+ " _ " + (int)Math.round(tempList.get(index).getX()/pixelWidth)
//										+ " Y " + (int)Math.round(p.getY()/pixelHeight) 
//										+ " _ " + (int)Math.round(tempList.get(index).getY()/pixelHeight), ProgressDialog.LOG);
								break sorting;
							}else{
								tempList.add(p);
								index++;
							}							
						}else{
							tempList.add(p);
							index++;
						}					
					}
					tempList.trimToSize();	
					if(!unsortedList.isEmpty()){						
						if(minLeftOverPoints > unsortedList.size()){
							list = new ArrayList<trackPoint2D>(tempList);
							bestIndex = se;
							minLeftOverPoints = unsortedList.size();
						}else{
							tempList.clear();
							tempList = null;
							System.gc();
						}
					}else{
//						progress.notifyMessage("frame " + frame + " - try " + (se+1) + "/" + startEnds.size() + ": " + unsortedList.size() + " points discarded.", ProgressDialog.LOG);
						list = new ArrayList<trackPoint2D>(tempList);
						bestIndex = se;
						minLeftOverPoints = 0;
						break findingBest;
					}
				}				
			}
//			progress.notifyMessage("frame " + frame + " best index: " + (bestIndex+1) + " with " + minLeftOverPoints + " left-over points.", ProgressDialog.LOG);
			nPoints = list.size();
			
//			//save unsorted list
//			for(int i = 0; i < nPoints; i++){
//				if(i != startIndex){
//					unsortedList.add(new trackPoint2D(shortestPath[chosenShortestPath].get(i).x*pixelWidth,
//							shortestPath[chosenShortestPath].get(i).y*pixelHeight));
//				}					
//			}
//			
//			//create sortedList (list)
//			{
//				list.add(startEnd);
//				
////				IJ.log(unsortedList.size() + " uls");
////				IJ.log(list.size() + " ls");
//				
//				int index = 0;
//				double distance;
//				int pIndex;
//				sorting: while(!unsortedList.isEmpty()){
//					distance = Double.POSITIVE_INFINITY; 
//					p = null;
//					pIndex = -1;
//					for(int i = 0; i < unsortedList.size(); i++){
//						if(getDistance(unsortedList.get(i),list.get(index),NOZ) < distance){
//							p = unsortedList.get(i);
//							pIndex = i;
//							distance = getDistance(unsortedList.get(i),list.get(index),NOZ);
//						}
//					}
//					if(p.equals(null)){
//						IJ.log("Problem no next point found");
//					}					
//					unsortedList.remove(pIndex);
//					if(Math.sqrt(Math.pow(p.getX()/pixelWidth-list.get(index).getX()/pixelWidth,2.0)+
//							Math.pow(p.getY()/pixelHeight-list.get(index).getY()/pixelHeight,2.0)) > constants.sqrt2){
//						progress.notifyMessage("frame " + frame + ": " + unsortedList.size() + " points discarded. X " + p.getX()/pixelWidth 
//								+ " _ " + list.get(index).getX()/pixelWidth
//								+ " Y " + p.getY()/pixelHeight + " _ " + list.get(index).getY()/pixelHeight, ProgressDialog.NOTIFICATION);
//						break sorting;
//					}else{
//						list.add(p);
//						index++;
//					}					
//				}
//			}
//			list.trimToSize();				
			
			//get statistics for first point				
				OvalRoi roi0 = new OvalRoi((int)Math.round(list.get(0).getX()/pixelWidth) - 8,
						(int)Math.round(list.get(0).getY()/pixelHeight) - 8, 17, 17);
				impMaxSave.setRoi(roi0);
				ImageStatistics stats0 = impMaxSave.getStatistics();
				double intensity0 = stats0.area * stats0.mean;
//				double [] COM0 = getXYCenterOfMass(impMaxSaveThresholded,roi0);
				
			//get statistics for last point
				OvalRoi roiE = new OvalRoi((int)Math.round(list.get(list.size()-1).getX()/pixelWidth) - 8,
						(int)Math.round(list.get(list.size()-1).getY()/pixelHeight) - 8, 17, 17);
				impMaxSave.setRoi(roiE);
				ImageStatistics statsE = impMaxSave.getStatistics();
				double intensityE = statsE.area*statsE.mean;
//				double [] COME = getXYCenterOfMass(impMaxSaveThresholded,roiE);
				
			//close images
				impMaxSave.changes = false;
				impMaxSave.close();
//				impMaxSaveThresholded.changes = false;
//				impMaxSaveThresholded.close();
				impIn.changes = false;
				impIn.close();
				
			//if first point is actually last point reverse list
			ArrayList <trackPoint2D> newList = new ArrayList <trackPoint2D>(nPoints);
			if(intensity0 < intensityE){
				//add center of mass point of head (only if not equal to first skeletal point)
//				if(list.get(nPoints-1).getX() == COME [0] && list.get(nPoints-1).getY() == COME [1]){
//					IJ.log("first point = COME");
//				}
//				else if(addCOM){
//					newList.ensureCapacity(nPoints+1);
//					newList.add(new trackPoint2D(COME [0], COME [1]));
//				}				
								
				//invert list
				for(int i = nPoints-1; i >= 0; i--){	
					newList.add(list.get(i));	
				}					
			}else{				
				//add center of mass point of head (only if not equal to first skeletal point)
//				if(list.get(0).getX() == COM0 [0] && list.get(0).getY() == COM0 [1]){
//					IJ.log("first point = COM0");
//				}
//				else if(addCOM){
//					newList.ensureCapacity(nPoints+1);
//					newList.add(new trackPoint2D(COM0 [0], COM0 [1]));
//				}
								
				for(int i = 0; i < nPoints; i++){	
					newList.add(list.get(i));	
				}
			}	
			newList.trimToSize();
			return new trace2D(newList, frame, maxRefDist, progress);
	}
		
	public static double [] getXYCenterOfMass (ImagePlus imp, Roi selection){
		/**
		 * Returns the X- and Y-coordinate of the center-of-mass of the Image imp (calculated within the Roi <selection>)
		 * at the current hyperstack-position.
		 * */
		double [] COM = {0.0, 0.0};
		double intensitySum = 0.0;
		if(selection == null){
			imp.deleteRoi();
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					intensitySum += imp.getStack().getVoxel(x, y, imp.getZ()-1);
					COM [0] += imp.getStack().getVoxel(x, y, imp.getZ()-1) * x * imp.getCalibration().pixelWidth;
					COM [1] += imp.getStack().getVoxel(x, y, imp.getZ()-1) * y * imp.getCalibration().pixelHeight;
				}
			}			
		}else{
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(selection.getPolygon().contains(x,y)){
						intensitySum += imp.getStack().getVoxel(x, y, imp.getZ()-1);
						COM [0] += imp.getStack().getVoxel(x, y, imp.getZ()-1) * x * imp.getCalibration().pixelWidth;
						COM [1] += imp.getStack().getVoxel(x, y, imp.getZ()-1) * y * imp.getCalibration().pixelHeight;						
					}
				}
			}
		}
		COM [0] /= intensitySum;
		COM [1] /= intensitySum;
		return COM;
	}
	
	private static void thresholdImage (ImagePlus imp, String algorithm){
		//threshold image
		IJ.setAutoThreshold(imp, (algorithm + " dark"));
		//TODO Replace with and check:
			//imp.getProcessor().setAutoThreshold(Method.valueOf(Method.class,algorithm), true);
		double minThreshold = imp.getProcessor().getMinThreshold();
		double imageMax = Math.pow(2.0,imp.getBitDepth())-1.0;
					
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				if(imp.getStack().getVoxel(x, y, 0) >= minThreshold){
					imp.getStack().setVoxel(x, y, 0, imageMax);
				}else{
					imp.getStack().setVoxel(x, y, 0, 0.0);
				}
			}
		}
//		IJ.log("bin ");userCheck(impMax);
	}
	
	/**
	 * For tethered sperm. Reverse Traces whose last point is closer to the median start point than the first point
	 * */
	public static void reverseReversedTraces(ArrayList<trace2D> traces, ProgressDialog progress){
		//remove 0 traces
		for(int i = traces.size()-1; i >= 0; i--){
			if(traces.get(i).getTracePoints().size()==0){
				traces.remove(i);
				progress.notifyMessage("trace at t = " + i + " has been removed (no points)", ProgressDialog.LOG);
			}
		}	
		traces.trimToSize();
		
		double [] stX = new double [traces.size()],
				stY = new double [traces.size()];
		
		for(int i = 0; i < traces.size(); i++){
			stX [i] = traces.get(i).getTracePoints().get(0).getX();
			stY [i] = traces.get(i).getTracePoints().get(0).getY();
		}
		
		double mediStX = tools.getMedian(stX);
		double mediStY = tools.getMedian(stY);
		
		for(int i = 0; i < traces.size(); i++){
			if(Math.sqrt(Math.pow(mediStX - stX [i], 2.0) + Math.pow(mediStY - stY [i],2.0))
				< Math.sqrt(Math.pow(mediStX - traces.get(i).getTracePoints().get(traces.get(i).getTracePoints().size()-1).getX(), 2.0)
					+ Math.pow(mediStY - traces.get(i).getTracePoints().get(traces.get(i).getTracePoints().size()-1).getY(), 2.0))){
			}else{
				//invert tracepoints
				traces.get(i).reverseTracePoints();
			}
			progress.updateBarText("Checking for reversed traces: trace nr " + i);
		}		
	}

	/**
	 * For freely swimming sperm.
	 * New from 14.08.2019, v1.0.9
	 * */
	public static void reverseReversedTracesOfFree(ArrayList<trace2D> traces, ProgressDialog progress,
			int frameDistanceForComparison){
		//remove 0 traces
		for(int i = traces.size()-1; i >= 0; i--){
			if(traces.get(i).getTracePoints().size()==0){
				traces.remove(i);
				progress.notifyMessage("trace at t = " + i + " has been removed (no points)", ProgressDialog.LOG);
			}
		}	
		traces.trimToSize();
		
		boolean [] correctFirst = new boolean [traces.size()];
		correctFirst [0] = false;
		double stX, stY, eX, eY, ostX, ostY;
		double [] ostXs = new double [(int)(frameDistanceForComparison/2.0)];
		double [] ostYs = new double [(int)(frameDistanceForComparison/2.0)];
		int counter;
		for(int i = 1; i < traces.size(); i++){
			stX = traces.get(i).getTracePoints().get(0).getX();
			stY = traces.get(i).getTracePoints().get(0).getY();
			
			eX = traces.get(i).getTracePoints().get(traces.get(i).getTracePoints().size()-1).getX();
			eY = traces.get(i).getTracePoints().get(traces.get(i).getTracePoints().size()-1).getY();
			
			Arrays.fill(ostXs, Double.POSITIVE_INFINITY);
			Arrays.fill(ostYs, Double.POSITIVE_INFINITY);
			counter = 0;
			for(int j = 1; j < (int)(frameDistanceForComparison/2.0)+1 && i-j >=0; j++){
				ostXs [j-1] = traces.get(i-j).getTracePoints().get(0).getX();
				ostYs [j-1]= traces.get(i-j).getTracePoints().get(0).getY();
				counter ++;
			}
			
			ostX = tools.getMedianOfRange(ostXs, 0, counter-1);
			ostY = tools.getMedianOfRange(ostYs, 0, counter-1);
			
			if(Math.sqrt(Math.pow(stX-ostX, 2.0) + Math.pow(stY-ostY, 2.0))
					<= Math.sqrt(Math.pow(eX-ostX, 2.0) + Math.pow(eY-ostY, 2.0))){
				//all is ok
			}else{
				//invert tracepoints
				traces.get(i).reverseTracePoints();
			}
			progress.updateBarText("Checking for reversed traces (free sperm): trace nr " + i);
		}
		
		//check whether the first point moves towards non ciliary regions
		if(frameDistanceForComparison<traces.size()){
			int counterWrong = 0;
			double xC, yC, xCAll, yCAll;
			int ct;
			for(int i = frameDistanceForComparison; i < traces.size(); i++){
				xCAll = 0.0; yCAll = 0.0; ct = 0;
				for(int j = -1; j <= 1 && i + j < traces.size(); j++){
					ct ++;
					xC = traces.get(i+j).getTracePoints().get(0).getX() 
							- traces.get(i+j).getTracePoints().get(traces.get(i+j).getTracePoints().size()-1).getX();
					yC = traces.get(i+j).getTracePoints().get(0).getY() 
							- traces.get(i+j).getTracePoints().get(traces.get(i+j).getTracePoints().size()-1).getY();
					xCAll += traces.get(i+j).getTracePoints().get(0).getX() - xC/2.0;
					yCAll += traces.get(i+j).getTracePoints().get(0).getY() - yC/2.0;
				}
				xCAll /= (double) ct;
				yCAll /= (double) ct;
						
				stX = traces.get(i-frameDistanceForComparison).getTracePoints().get(0).getX();
				stY = traces.get(i-frameDistanceForComparison).getTracePoints().get(0).getY();
				
				eX = traces.get(i-frameDistanceForComparison).getTracePoints().get(
						traces.get(i-frameDistanceForComparison).getTracePoints().size()-1).getX();
				eY = traces.get(i-frameDistanceForComparison).getTracePoints().get(
						traces.get(i-frameDistanceForComparison).getTracePoints().size()-1).getY();
				
				if(Math.sqrt(Math.pow(xCAll-stX, 2.0) + Math.pow(yCAll-stY, 2.0))
						> Math.sqrt(Math.pow(xCAll-eX, 2.0) + Math.pow(yCAll-eY, 2.0))){
					counterWrong ++;
				}	
				progress.updateBarText("Checking general reversement: trace nr " + i);
			}
			if((double) counterWrong / (double) (traces.size() - frameDistanceForComparison) > 0.5){
				for(int i = 0; i < traces.size(); i++){
					//invert tracepoints
					traces.get(i).reverseTracePoints();
				}
			}
		}		
	}
	
	/**
	 * Sets a unique trace starting point (recommended for tethered sperm)
	 * */
	public static void unifyStartPoints(ArrayList<trace2D> traces, ProgressDialog progress){
//		//remove 0 traces
//		for(int i = traces.size()-1; i >= 0; i--){
//			if(traces.get(i).getTracePoints().size()==0){
//				traces.remove(i);
//				progress.notifyMessage("trace at t = " + i + " has been removed (no points)", ProgressDialog.LOG);
//			}
//		}	
//		traces.trimToSize();
		
		double [] stX = new double [traces.size()],
				stY = new double [traces.size()];
		
		for(int i = 0; i < traces.size(); i++){
			stX [i] = traces.get(i).getTracePoints().get(0).getX();
			stY [i] = traces.get(i).getTracePoints().get(0).getY();
		}
		
		double mediStX = tools.getMedian(stX);
		double mediStY = tools.getMedian(stY);
		
		//save new start point
		for(int i = 0; i < traces.size(); i++){
			traces.get(i).getTracePoints().add(0, new trackPoint2D(mediStX, mediStY));
//			progress.updateBarText("Saving merged start point: trace nr " + i);
		}
	}
	
	/**
	 * Add the center of mass around the first point as first point
	 * */
	public static void add1stCOM (ArrayList<trace2D> traces, ImagePlus imp, ProgressDialog progress){
		//remove 0 traces
		for(int i = traces.size()-1; i >= 0; i--){
			if(traces.get(i).getTracePoints().size()==0){
				traces.remove(i);
				progress.notifyMessage("trace at t = " + i + " has been removed (no points)", ProgressDialog.LOG);
			}
		}	
		traces.trimToSize();
		
		ImagePlus impT;
		double [] COM;
		for(int i = 0; i < traces.size(); i++){
			impT = impProcessing.getSingleImageFromStack(imp, imp.getStackIndex(1, 1, i+1)-1);
			
			OvalRoi roi0 = new OvalRoi((int)Math.round(traces.get(i).getTracePoints().get(0).getX()/impT.getCalibration().pixelWidth) - 8,
					(int)Math.round(traces.get(i).getTracePoints().get(0).getY()/impT.getCalibration().pixelHeight) - 8, 17, 17);
			impT.setRoi(roi0);
			COM = getXYCenterOfMass(impT,roi0);
			traces.get(i).getTracePoints().add(0, new trackPoint2D(COM [0], COM [1]));
			progress.updateBarText("COM added: trace nr " + i);
		}		
	}	
	
	public static void adjustPointsViaNormalVector(ImagePlus imp, ArrayList<trace2D> traces, ProgressDialog progress, boolean saveNormalVectorRoiSet, 
			String saveDirectory, int vectorSize, double normalRad, boolean smoothNormal, boolean preventHeadFromCorr, int preventPoints, boolean filterFits){
		if(imp.getNSlices()>4)	IJ.error("to many slices for correction via normal vector...");
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		int calculationRadius = (int)Math.round(normalRad/imp.getCalibration().pixelWidth);
		
		RoiManager rm = RoiManager.getInstance();
		if (rm==null) rm = new RoiManager();
		
		int channel = 1;	//no multichannel implemented so far!
				
		//initialize variables
		final int halfSize = (vectorSize-(vectorSize%2))/2;
		
		double x1, y1, x2, y2;
		int t;
		ArrayList<trackPoint2D> points;
		int nrOfPoints;
		double [] radii = new double [4];
		int radius;
		
		int undefined = 0;
		double normalPointsX [];
		double normalPointsY [];
	
		CurveFitter cf;
		double [] parameters;	
		double rSquare;
		
	//calculate
		
		String undefinedPoints;
		for(int i = 0; i < traces.size(); i++){
			traces.get(i).xyCorrected = true;
			undefinedPoints = "";
			if(traces.get(i).getTracePoints().size()>0){
				if(progress.isStopped())	return;
				progress.updateBarText("Improving x,y position " + (i+1) + "/" + traces.size());
				
				t = traces.get(i).getFrame();
				points = traces.get(i).getTracePoints();
				nrOfPoints = points.size();			
				
				if(saveNormalVectorRoiSet){
					rm.reset();
				}
				
				
				
				//generate vectors
				x1 = (double)points.get(0).getX();
				y1 = (double)points.get(0).getY();
				x2 = (double)points.get(0).getX();
				y2 = (double)points.get(0).getY();
				for(int j = nrOfPoints-1; j >= 0; j--){
					//calculate tangential vector					
					searchUpstream: for(int vu = halfSize; vu >= 0; vu--){
						if(j-vu >= 0){
							x1 = (double)points.get(j-vu).getX();
							y1 = (double)points.get(j-vu).getY();
							break searchUpstream;
						}
					}
					
					searchDownstream: for(int vd = halfSize; vd >= 0; vd--){
						if(j+vd < nrOfPoints){
							x2 = (double)points.get(j+vd).getX();
							y2 = (double)points.get(j+vd).getY();
							break searchDownstream;
						}
					}					
					points.get(j).setVectors(x2-x1, y2-y1);
				}
				
				//fit along normal vector				
				undefined = 0;				
				filterPoints: for(int j = nrOfPoints-1; j >= 0; j--){
					//adjust calculation radius if touching borders
					radii [0] = Math.sqrt(Math.pow(((double)(w-2)*imp.getCalibration().pixelWidth - points.get(j).getX()) / (points.get(j).getNormalVector()[0]*imp.getCalibration().pixelWidth),2.0));
//					if(radii [0] < 0)	radii [0] = Double.MAX_VALUE;
					radii [1] = Math.sqrt(Math.pow(((double)(h-2)*imp.getCalibration().pixelHeight - points.get(j).getY()) / (points.get(j).getNormalVector()[1]*imp.getCalibration().pixelHeight),2.0));
//					if(radii [1] < 0)	radii [1] = Double.MAX_VALUE;
					radii [2] = Math.sqrt(Math.pow((points.get(j).getX()) / (points.get(j).getNormalVector()[0]*imp.getCalibration().pixelWidth),2.0));
//					if(radii [2] < 0)	radii [2] = Double.MAX_VALUE;
					radii [3] = Math.sqrt(Math.pow((points.get(j).getY()) / (points.get(j).getNormalVector()[1]*imp.getCalibration().pixelHeight),2.0));
//					if(radii [3] < 0)	radii [3] = Double.MAX_VALUE;
					
					Arrays.sort(radii);
					radius = (int)Math.round(radii [0]);
					if(radius > calculationRadius){
//						IJ.log("r1" + radius);
						radius = calculationRadius;
//						IJ.log("r2" + radius);
					}else{
//						IJ.log(" x " + points.get(j).getOrX() + " y " + points.get(j).getOrY() + " w " + w + " h " + h);
//						IJ.log(" ri " + radii [0]);
//						IJ.log(" ri " + radii [1]);
//						IJ.log(" ri " + radii [2]);
//						IJ.log(" ri " + radii [3]);
//						IJ.log(" nx " + points.get(j).getNormalVector()[0]);
//						IJ.log(" ny " + points.get(j).getNormalVector()[1]);
					}
					
					//save vector Roi
					if(saveNormalVectorRoiSet){
						Line vectorRoi = points.get(j).getVectorLine(imp);
						vectorRoi.setName("vector " + j);
						rm.addRoi(vectorRoi);	
						
						Line normalVectorRoi = points.get(j).getNormalVectorLine(imp,calculationRadius);
						normalVectorRoi.setName("normal vector " + j);
						rm.addRoi(normalVectorRoi);	
					}
					
					//Fit along normal vector
					normalPointsX = new double [radius*2+1];
					normalPointsY = new double [radius*2+1];
					
					{
						//obtain data points for fitting		
						try{
							normalPointsX [radius] = 0.0;
							normalPointsY [radius] = impProcessing.getInterpolatedIntensity2D(imp, points.get(j).getX(),	//x 
							points.get(j).getY(),	//y
							imp.getStackIndex(channel, 1, t+1)-1);	//intensity		
							
							for(int p = 0; p < radius; p++){
								normalPointsX [p + 1 + radius] = (p+1) * (points.get(j).getNormalVectorLength() * imp.getCalibration().pixelWidth);
								normalPointsY [p + 1 + radius] = impProcessing.getInterpolatedIntensity2D(imp,
										(double)points.get(j).getX() + (p+1) * (points.get(j).getNormalVector()[0] * imp.getCalibration().pixelWidth),
										(double)points.get(j).getY() + (p+1) * (points.get(j).getNormalVector()[1] * imp.getCalibration().pixelHeight),
										imp.getStackIndex(channel, 1, t+1)-1);			
								
								normalPointsX [radius - 1 - p] = (-1) * (p+1) * (points.get(j).getNormalVectorLength() * imp.getCalibration().pixelWidth);
								normalPointsY [radius - 1 - p] = impProcessing.getInterpolatedIntensity2D(imp,
										(double)points.get(j).getX() - (p+1) * (points.get(j).getNormalVector()[0] * imp.getCalibration().pixelWidth),
										(double)points.get(j).getY() - (p+1) * (points.get(j).getNormalVector()[1] * imp.getCalibration().pixelHeight),
										imp.getStackIndex(channel, 1, t+1)-1);
							}
						}catch(Exception e){
							String out = "";
							for(int err = 0; err < e.getStackTrace().length; err++){
								out += " \n " + e.getStackTrace()[err].toString();
							}
							progress.notifyMessage("Error in grabbing intensities for normal on x " + points.get(j).getX() + " y " + points.get(j).getY() + " stacki " + (imp.getStackIndex(channel, 1, t+1)-1) + ", " 
									+ "w" + imp.getWidth() + " h" + imp.getHeight() + " ss" + imp.getStackSize() + ". Error: " + out,ProgressDialog.LOG);
							undefinedPoints += " " + j + ",";
							points.remove(j);
							points.trimToSize();
							undefined++;
							continue filterPoints;
						}
						
						//eventually smooth normal
							if(smoothNormal){
								for(int n = 0; n < normalPointsY .length; n++){
									double newIntensity = normalPointsY [n];								
									if(n==0){
										if(normalPointsY .length>1){
											newIntensity += normalPointsY [n+1];
											newIntensity /= 2.0;
										}									
									}else if(n==normalPointsY .length-1){
										newIntensity += normalPointsY [n-1];
										newIntensity /= 2.0;
									}else{
										newIntensity += normalPointsY [n+1];
										newIntensity += normalPointsY [n-1];
										newIntensity /= 3.0;
									}
									normalPointsY [n] = newIntensity;	
								}
							}
						
						//gauss fit						
//							if(s == 0 && t == 0 && j == 30)	showAsPlot(normalPointsX,normalPointsY);
							
							cf = new CurveFitter(normalPointsX , normalPointsY );
							cf.setMaxIterations(1000);	
							cf.doFit(CurveFitter.GAUSSIAN);
													
							parameters = cf.getParams();	
							// case GAUSSIAN: p[0]+(p[1]-p[0])*Math.exp(-(x-p[2])*(x-p[2])/(2.0*p[3]*p[3]))
							rSquare= cf.getRSquared();
					        
						//filter fits
							//1. offset >= 0.0, 2. width < total calculated normal width, 3. rSquare > 0.8, 4. maximumShift<radius
							
							if(rSquare >= 0.8
								&& parameters [0] >= 0.0
								&& parameters [3] <= imp.getCalibration().pixelWidth * radius * 2.0 * points.get(j).getNormalVectorLength()
								&& Math.abs(parameters [2]) <= imp.getCalibration().pixelWidth * radius * points.get(j).getNormalVectorLength()){
														
								if(preventHeadFromCorr && j < preventPoints){
									points.get(j).widthGaussFit(radius, normalPointsX, normalPointsY, parameters, false);
								}else{
									points.get(j).widthGaussFit(radius, normalPointsX, normalPointsY, parameters, true);
								}	
							}else if(!filterFits){
								points.get(j).widthGaussFit(radius, normalPointsX, normalPointsY, parameters, false);
							}else{
								if(preventHeadFromCorr && j < preventPoints){
									//Do not remove head points, if prevented
								}else{
									//remove point
									undefinedPoints += " " + j + ",";
									points.remove(j);
									points.trimToSize();
									undefined++;
								}
							}	
					}
					
							
				} 			
				
				if(saveNormalVectorRoiSet){
					rm.runCommand("Save", saveDirectory + "NormVecRois_" + t + ".zip");	
				}	
				
				progress.addToBar(0.1*(1.0/traces.size()));
				if(undefined>0)	progress.notifyMessage("t=" + i + ": " + undefined + " points removed where XY gauss fit failed:" + undefinedPoints,ProgressDialog.LOG);			
			}
			if(i%50 == 0)	System.gc();
		}
	}
	
	public static void updateWidthFitAndGetRelativeZ(ImagePlus imp, ArrayList<trace2D> traces, ProgressDialog progress, boolean saveNormalVectorRoiSet, 
			String saveDirectory, int vectorSize, double normalRad, boolean smoothNormal, boolean preventHeadFromCorr, int preventPoints){
		if(imp.getNSlices()>4)	IJ.error("too many slices for correction via normal vector...");
		int w = imp.getWidth();
		int h = imp.getHeight();
		int calculationRadius = (int)Math.round(normalRad / imp.getCalibration().pixelWidth);
		
		RoiManager rm = RoiManager.getInstance();
		if (rm==null) rm = new RoiManager();
		
		int channel = 1;	//no multichannel implemented so far!
			
		//initialize variables
			final int halfSize = (vectorSize-(vectorSize%2))/2;
			
			String undefinedPoints;
			double x1, y1, x2, y2;
			int t;
			ArrayList<trackPoint2D> points;
			int nrOfPoints;
			double [] radii = new double [4];
			int radius  = Integer.MIN_VALUE;
			
			int undefined = 0;
			double normalPointsX [];
			double normalPointsY [];
		
			CurveFitter cf;
			double [] parameters;	
			double rSquare;
			
		//calculate
		for(int i = 0; i < traces.size(); i++){
			if(traces.get(i).getTracePoints().size()>0){
				undefinedPoints = "";
				if(progress.isStopped())	return;
				progress.updateBarText("Obtaining width fit data " + (i+1) + "/" + traces.size());
				
				t = traces.get(i).getFrame();
				points = traces.get(i).getTracePoints();
				nrOfPoints = points.size();			
					
				if(saveNormalVectorRoiSet){
					rm.reset();
				}
				
				//generate vectors				
				x1 = points.get(0).getX();
				y1 = points.get(0).getY();
				x2 = points.get(0).getX();
				y2 = points.get(0).getY();
				for(int j = points.size()-1; j >= 0; j--){
					//generate vectors
					searchUpstream: for(int vu = halfSize; vu >= 0; vu--){
						if(j-vu >= 0){
							x1 = points.get(j-vu).getX();
							y1 = points.get(j-vu).getY();
							break searchUpstream;
						}
					}
					
					searchDownstream: for(int vd = halfSize; vd >= 0; vd--){
						if(j+vd < nrOfPoints){
							x2 = points.get(j+vd).getX();
							y2 = points.get(j+vd).getY();
							break searchDownstream;
						}
					}					
					//TODO test for x2-x1==0 and eventually remove point
					points.get(j).setVectors(x2-x1, y2-y1);
				}
				
				undefined = 0;				
				for(int j = nrOfPoints-1; j >= 0; j--){
					try{
						//adjust calculation radius if touching borders
						radii [0] = Math.sqrt(Math.pow(((double)(w-2)*imp.getCalibration().pixelWidth - points.get(j).getX()) / (points.get(j).getNormalVector()[0]*imp.getCalibration().pixelWidth),2.0));
//						if(radii [0] < 0)	radii [0] = Double.MAX_VALUE;
						radii [1] = Math.sqrt(Math.pow(((double)(h-2)*imp.getCalibration().pixelHeight - points.get(j).getY()) / (points.get(j).getNormalVector()[1]*imp.getCalibration().pixelHeight),2.0));
//						if(radii [1] < 0)	radii [1] = Double.MAX_VALUE;
						radii [2] = Math.sqrt(Math.pow((points.get(j).getX()-1*imp.getCalibration().pixelWidth) / (points.get(j).getNormalVector()[0]*imp.getCalibration().pixelWidth),2.0));
//						if(radii [2] < 0)	radii [2] = Double.MAX_VALUE;
						radii [3] = Math.sqrt(Math.pow((points.get(j).getY()-1*imp.getCalibration().pixelHeight) / (points.get(j).getNormalVector()[1]*imp.getCalibration().pixelHeight),2.0));
//						if(radii [3] < 0)	radii [3] = Double.MAX_VALUE;
						
						Arrays.sort(radii);
						radius = (int)Math.round(radii [0]);					
						if(radius > calculationRadius){
//							IJ.log("r1" + radius);
							radius = calculationRadius;
//							IJ.log("r2" + radius);
						}else{
//							IJ.log(" x " + (points.get(j).getOrX() / imp.getCalibration().pixelWidth) + " y " 
//									+ (points.get(j).getOrY() / imp.getCalibration().pixelHeight) + " w " + w + " h " + h);
//							IJ.log(" ri " + radii [0]);
//							IJ.log(" ri " + radii [1]);
//							IJ.log(" ri " + radii [2]);
//							IJ.log(" ri " + radii [3]);
//							IJ.log(" nx " + points.get(j).getNormalVector()[0]);
//							IJ.log(" ny " + points.get(j).getNormalVector()[1]);
						}
						
						//save vector Roi-set
						if(saveNormalVectorRoiSet){
							Line vectorRoi = points.get(j).getVectorLine(imp);
							vectorRoi.setName("vector " + j);
							rm.addRoi(vectorRoi);	
							
							Line normalVectorRoi = points.get(j).getNormalVectorLine(imp,calculationRadius);
							normalVectorRoi.setName("normal vector " + j);
							rm.addRoi(normalVectorRoi);	
						}
						
						normalPointsX = new double [radius*2+1];
						normalPointsY = new double [radius*2+1];
						if(radius > 1){
						//obtain data points for fitting						
							normalPointsX [radius] = 0.0;
							normalPointsY [radius] = impProcessing.getInterpolatedIntensity2D(imp, points.get(j).getX(),	//x 
									points.get(j).getY(),	//y
									imp.getStackIndex(channel, 1, t+1)-1);	//intensity						
							
							for(int p = 0; p < radius; p++){
								normalPointsX [p + 1 + radius] = (p+1) * (points.get(j).getNormalVectorLength() * imp.getCalibration().pixelWidth);
								normalPointsY [p + 1 + radius] = impProcessing.getInterpolatedIntensity2D(imp,
										(double)points.get(j).getX() + (p+1) * (points.get(j).getNormalVector()[0] * imp.getCalibration().pixelWidth),
										(double)points.get(j).getY() + (p+1) * (points.get(j).getNormalVector()[1] * imp.getCalibration().pixelHeight),
										imp.getStackIndex(channel, 1, t+1)-1);			
								
								normalPointsX [radius - 1 - p] = (-1) * (p+1) * (points.get(j).getNormalVectorLength() * imp.getCalibration().pixelWidth);
								normalPointsY [radius - 1 - p] = impProcessing.getInterpolatedIntensity2D(imp,
										(double)points.get(j).getX() - (p+1) * (points.get(j).getNormalVector()[0] * imp.getCalibration().pixelWidth),
										(double)points.get(j).getY() - (p+1) * (points.get(j).getNormalVector()[1] * imp.getCalibration().pixelHeight),
										imp.getStackIndex(channel, 1, t+1)-1);
							}
						
						//eventually smooth normal
							if(smoothNormal){
								for(int n = 0; n < normalPointsY .length; n++){
									if(n==0){
										if(normalPointsY .length>1){
											normalPointsY [n] += normalPointsY [n+1];
											normalPointsY [n] /= 2.0;
										}									
									}else if(n==normalPointsY .length-1){
										normalPointsY [n] += normalPointsY [n-1];
										normalPointsY [n] /= 2.0;
									}else{
										normalPointsY [n] += normalPointsY [n+1];
										normalPointsY [n] += normalPointsY [n-1];
										normalPointsY [n] /= 3.0;
									}
								}
							}
						
						//gauss fit						
//								if(s == 0 && t == 0 && j == 30)	showAsPlot(normalPointsX[s],normalPointsY[s]);
							
//								if(t == 0){
//									IJ.log("s" + s + " t" + t + " j" + j + "");
//									showAsPlot(normalPointsX[s],normalPointsY[s]);
//								}
							
							cf = new CurveFitter(normalPointsX, normalPointsY);
							cf.setMaxIterations(1000);	
							cf.doFit(CurveFitter.GAUSSIAN);
													
							parameters = cf.getParams();	
							// case GAUSSIAN: p[0]+(p[1]-p[0])*Math.exp(-(x-p[2])*(x-p[2])/(2.0*p[3]*p[3]))
							rSquare= cf.getRSquared();
						        
						//filter fits
							//1. offset >= 0.0, 2. width < total calculated normal width, 3. rSquare > 0.8, 4. maximumShift<radius
							
							if(rSquare>=0.8
								&& parameters [0] >= 0.0
								&& parameters [3] <= imp.getCalibration().pixelWidth * radius * 2.0 * points.get(j).getNormalVectorLength()
								&& Math.abs(parameters [2]) <= imp.getCalibration().pixelWidth * radius * points.get(j).getNormalVectorLength()){
								
								points.get(j).widthGaussFit(radius, normalPointsX, normalPointsY, parameters, false);
								
							}else{
								if(preventHeadFromCorr && j < preventPoints){
									//Do not remove head points, if prevented
								}else{
									//do not remove because old fit still available
									undefinedPoints += " " + j + ",";
//										points.remove(j);
//										points.trimToSize();
									undefined++;	
								}
								
															
							}							
						}	
					}catch(Exception e){
						if(preventHeadFromCorr && j < preventPoints){
							//Do not remove head points, if prevented
						}else{
							//remove point do not remove because old fit still available
							undefinedPoints += " " + j + ",";
//								points.remove(j);
//								points.trimToSize();
							undefined++;	
							String out = "";
							for(int err = 0; err < e.getStackTrace().length; err++){
								out += " \n " + e.getStackTrace()[err].toString();
							}	
							progress.notifyMessage("t=" + i + ", point " + j + ": gauss fit failed due to error - secret error code " + radius
									+ "	-	" + ((double)points.get(j).getX() + (radius) * (points.get(j).getNormalVector()[0] * imp.getCalibration().pixelWidth))
									+ "	-	" + ((double)points.get(j).getY() + (radius) * (points.get(j).getNormalVector()[1] * imp.getCalibration().pixelHeight))
									+ "	-	" + (imp.getStackIndex(channel, 1, t+1)-1)
									+ "	#	" + ((double)points.get(j).getX() - (radius) * (points.get(j).getNormalVector()[0] * imp.getCalibration().pixelWidth))
									+ "	-	" + ((double)points.get(j).getY() - (radius) * (points.get(j).getNormalVector()[1] * imp.getCalibration().pixelHeight))
									+ "	-	" + (imp.getStackIndex(channel, 1, t+1)-1) + " - error: " + out, ProgressDialog.LOG);							
						}
					}
											
				} 			
				
				if(saveNormalVectorRoiSet){
					rm.runCommand("Save", saveDirectory + "NormVecRoisIter2_" + t + ".zip");	
				}
				
				progress.addToBar(0.1*(1.0/traces.size()));
				if(undefined>0)	progress.notifyMessage("t=" + i + ": " + undefined + " points where gauss fit update failed:" + undefinedPoints,ProgressDialog.LOG);
			}
			
			traces.get(i).relZ = true;
		}	
	}
	
//	public static double [] projectPointToLine3D (double px, double py, double pz, double lx1, double ly1, double lz1, double lx2, double ly2, double lz2){
//		//calculate 
//		double m = (ly2-ly1)/(lx2-lx1);			
//		
//		//find dislocation of normal line (m*x+b=y)
//		double bNormal = py - ((1.0/m) * px);		
//		
//		//calculate new x
//		double x = ((ly1 - (m * lx1))-bNormal)/((1.0/m)-m);
//		
//		return new double [] {x,(1.0/m) * x + bNormal};
//		
//		//LONG WAY CALCULATION
////		//calculate 
////		double m = (ly2-ly1)/(lx2-lx1);
////		//find dislocation of line (m*x+b=y)
////		double b = ly1 - (m * lx1);
////				
////		//calculate slope of normal line
////		double mNormal = 1.0/m;
////		//find dislocation of normal line (m*x+b=y)
////		double bNormal = py - (mNormal * px);
////		
////		//calculate new x
////		double x = (b-bNormal)/(mNormal-m);
////		double y = mNormal * x + bNormal;
//	}
	
	public static void interpolateZLinear(ArrayList<trace2D> traces, ProgressDialog progress, double acceptedDist, int plusMinusPoints){		
		for(int i = 0; i < traces.size(); i++){
			if(traces.get(i).getTracePoints().size()>0){
				if(progress.isStopped()) return;			
				progress.updateBarText("Interpolate z position " + (i+1) + "/" + traces.size());
				
				traces.get(i).interpolateZLinear(acceptedDist, plusMinusPoints);
				
				progress.addToBar(0.2*(1.0/traces.size()));
			}			
		}
	}
	
	public static void removeProblematicTraces(ProgressDialog progress, ArrayList<trace2D> traces){
		/**
		 * Filters the ArrayList of traces
		 * A trace will be removed, if it contains points whose oriented coordinates are not within a range of -300 to +300 µm.
		 * Encoding determines the type of z interpolation used for the analysis
		 * */
//		double min [], max [];
		boolean keep;
  		for(int i = 0; i < traces.size(); i++){
//  			min = new double [] {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
//  			max = new double [] {0.0, 0.0, 0.0};  			
//  			keep = true;
  			
  			if(traces.get(i).getTracePoints().size() > 0 && traces.get(i).oriented){	//Double.isNaN(traces.get(i).getThetaDegree(encoding))==false
//				for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
//					for(int xyz = 0; xyz < 2; xyz++){	//check only xy problems
//						if(traces.get(i).getTracePoints().get(j).get2DOrientedVector(NOZ)[xyz] > max [xyz]){
//							max [xyz] = traces.get(i).getTracePoints().get(j).get2DOrientedVector(NOZ) [xyz];
//						}
//						if(traces.get(i).getTracePoints().get(j).get2DOrientedVector(NOZ)[xyz] < min [xyz]){
//							min [xyz] = traces.get(i).getTracePoints().get(j).get2DOrientedVector(NOZ) [xyz];
//						}
//					}	
//				}			
//				
//				for(int xyz = 0; xyz < 2; xyz++){	//remove only xy problems
//	  				if(min[xyz] < -300.0){
//	  					keep = false;
//	  				}
//	  				if(max[xyz] > 300.0){
//	  					keep = false;
//	  				}
//	  			}  	
			}else{
				progress.notifyMessage("Trace " + traces.get(i).getFrame() + " needs to be removed (no points present)", ProgressDialog.LOG);
				traces.remove(i);
  				traces.trimToSize();
			}
  			
//  			if(!keep){
//  				progress.notifyMessage("Trace " + traces.get(i).getFrame() + " needs to be removed (encoding: " + encoding + ")", ProgressDialog.LOG);
//  				progress.notifyMessage("   min " + min [0] + " - " + min [1] + " - " + min [2] + "", ProgressDialog.LOG);
//  				progress.notifyMessage("   max " + max [0] + " - " + max [1] + " - " + max [2] + "", ProgressDialog.LOG);  	
//	  			traces.remove(i);
//  				traces.trimToSize();
//  			}
  		}	
	}
	
	public static void saveTraceImage(ImagePlus imp, ArrayList<trace2D> traces, int encoding, String path){
		if(imp.getNChannels()>1)	IJ.error("generation of trace image not implemented for multi-channel stacks...");
		int channel = 0;
		String ending = "_ti";
		
		double [][][][] saveImage = new double [imp.getWidth()][imp.getHeight()][imp.getNFrames()][2];
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int t = 0; t < imp.getNFrames(); t++){
					saveImage [x][y][t][0] = 0.0;
					saveImage [x][y][t][1] = 0.0;
				}
			}
		}
		
		ImagePlus traceImp = IJ.createHyperStack("trace image", imp.getWidth(), imp.getHeight(), imp.getNChannels(), 1, imp.getNFrames(), 8);
		traceImp.setCalibration(imp.getCalibration());
		final double maxIntensity = 255.0;
		
		//save positions
		if (encoding == NOZ){
			if(!traces.get(0).xyCorrected){
				ending = "_ti_rawskl";	
			}		
			
			for(int i = 0; i < traces.size(); i++){
				int t = traces.get(i).getFrame();
				for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
					int z = traceImp.getStackIndex(channel+1, 1, t+1)-1;
					saveImage[(int)Math.round(traces.get(i).getTracePoints().get(j).getX()/imp.getCalibration().pixelWidth)]
							[(int)Math.round(traces.get(i).getTracePoints().get(j).getY()/imp.getCalibration().pixelHeight)]
							[z][0] = maxIntensity;
					saveImage[(int)Math.round(traces.get(i).getTracePoints().get(j).getX()/imp.getCalibration().pixelWidth)]
							[(int)Math.round(traces.get(i).getTracePoints().get(j).getY()/imp.getCalibration().pixelHeight)]
							[z][1] = 1.0;
					
				}			
			}
		}else{									
			ending = "_ti_zC";
			if(encoding == MEDIANZ){
				ending += "median";
			}else if(encoding == MEANZ){
				ending += "mean";
			}
			
			for(int i = 0; i < traces.size(); i++){
				int t = traces.get(i).getFrame();				
				for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
					int z = traceImp.getStackIndex(channel+1, 1, t+1)-1;
					saveImage[(int)Math.round(traces.get(i).getTracePoints().get(j).getX()/imp.getCalibration().pixelWidth)]
							[(int)Math.round(traces.get(i).getTracePoints().get(j).getY()/imp.getCalibration().pixelHeight)]
							[z][0] += tools.getEncodedIntensity8bit(traces.get(i).getTracePoints().get(j).getZ(encoding), KYMIN_Z, KYMAX_Z);
					saveImage[(int)Math.round(traces.get(i).getTracePoints().get(j).getX()/imp.getCalibration().pixelWidth)]
							[(int)Math.round(traces.get(i).getTracePoints().get(j).getY()/imp.getCalibration().pixelHeight)]
							[z][1] += 1.0;
				}			
			}
			
			//save image metadata
			TextPanel tp = new TextPanel("Metadata");
			tp.append("Image information for image:	" + path.substring(path.lastIndexOf(System.getProperty("file.separator"))) + ending + ".tif");
			tp.append("");
			tp.append("dimension		minimum	maximum");
			tp.append("gray value (1.0-254.0):	z information [um fit width]	" + constants.df6US.format(KYMIN_Z) + "	" + constants.df6US.format(KYMAX_Z));
			tp.append("");
			tp.append("code#"+KYMIN_Z+"-"+KYMAX_Z+"");
			addFooter(tp);
		  	tp.saveAs(path + ending + "_info.txt");
		}		
		
		//write to image
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int t = 0; t < imp.getNFrames(); t++){
					int z = traceImp.getStackIndex(channel+1, 1, t+1)-1;
					if(saveImage[x][y][z][1]>0.0){						
						traceImp.getStack().setVoxel(x, y, z, saveImage[x][y][z][0]/saveImage[x][y][z][1]); 
					}
				}
			}
		}
		
		impProcessing.setOptimalDisplayRange(traceImp, false);
		IJ.run(traceImp, "Fire", "");		
		IJ.saveAsTiff(traceImp, path + ending + ".tif");
		traceImp.changes = false;
		traceImp.close();
	}

	public static void saveXYWidthGraph(ArrayList<trace2D> traces, double calibration, String path){	//resolution = scaling factor
		int slices = 1;
		
		//get parameters
			double length = 0.0;
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			int tMax = 0;
			for(int i = 0; i < traces.size(); i++){				
				if(traces.get(i).getFrame() > tMax)	tMax = traces.get(i).getFrame();
				if(traces.get(i).getTracePoints().size() > 0){
					for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
						if(traces.get(i).getTracePoints().get(j).getXYGaussWidth() != 0.0){
							if(traces.get(i).getTracePoints().get(j).getArcLengthPos() > length){
								length = traces.get(i).getTracePoints().get(j).getArcLengthPos();
							}
							if(traces.get(i).getTracePoints().get(j).getXYGaussWidth() > max){
								max = traces.get(i).getTracePoints().get(j).getXYGaussWidth();
							}
							if(traces.get(i).getTracePoints().get(j).getXYGaussWidth() < min){
								min = traces.get(i).getTracePoints().get(j).getXYGaussWidth();
							}
						}
						
					}					
				}
			}
						
			if(tMax != traces.size()-1)	IJ.log("saveXYWidthGraph: tMax = " + tMax + " traces.size = " + traces.size());
			
		//create Image
			int width = (int)Math.round(length/calibration)+1;
			ImagePlus codeImp = IJ.createImage("Width graph", width, tMax+1, slices, 8);

			double values [][] = new double [width][2];
			for(int i = 0; i < traces.size(); i++){
				for(int a = 0; a < width; a++){
					values [a][0] = 0.0;
					values [a][1] = 0.0;
				}
								
				for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
					if(traces.get(i).getTracePoints().get(j).getXYGaussWidth() != 0.0){
						values [(int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos()/calibration)]
								[0] += tools.getEncodedIntensity8bit (traces.get(i).getTracePoints().get(j).getXYGaussWidth(), min, max);
						values [(int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos()/calibration)]
								[1] += 1.0;		
					}												
				}
				
				for(int a = 0; a < width; a++){
					if(values[a][1]!=0.0){
						codeImp.getStack().setVoxel(a, traces.get(i).getFrame(), 0, (values [a][0] / values [a][1]));
//					}else{
//						IJ.log("no value in width table " + a + " t=" + i);
					}
				}
			}
			codeImp.getCalibration().pixelWidth = calibration;
			impProcessing.setOptimalDisplayRange(codeImp, false);
			IJ.run(codeImp, "Fire", "");
			
			IJ.saveAsTiff(codeImp, path + "_w.tif");
			codeImp.changes = false;
			codeImp.close();
			
		//save image metadata
			TextPanel tp = new TextPanel("Metadata");
			tp.append("Image information for image:	" + path.substring(path.lastIndexOf(System.getProperty("file.separator"))) + "_w.tif");
			tp.append("");
			tp.append("dimension		minimum	maximum");
			tp.append("x axis:	xy arc length	0	" + constants.df0.format((int)Math.round(length/calibration)+1) + "	calibration: " + constants.df6US.format(calibration) + "");
			tp.append("y axis:	time [frame]	0	" + constants.df0.format(tMax));
			tp.append("gray value (1.0-254.0):	xy gauss fit width	" + constants.df6US.format(min) + "	" + constants.df6US.format(max));
			tp.append("");
			tp.append("code#"+min+"-"+max+"+"+(1.0/calibration));
			addFooter(tp);
		  	tp.saveAs(path + "_w_info.txt");
	}
	
	public static void saveOrientedTraceImage(ImagePlus imp, ArrayList<trace2D> traces, int encoding, String path, double calibration){
		String ending = "";
		
		//get min / max values
		int	tMax = 0;
		for(int i = 0; i < traces.size(); i++){			
			if(!(traces.get(i).getTracePoints().size() > 0)) continue;
			if(!traces.get(i).oriented)	continue;			
			if(traces.get(i).getFrame() > tMax)	tMax = traces.get(i).getFrame();
		}
		
		//initialize image and array
		int width = (int)Math.round((KYMAX_X - KYMIN_X)/calibration) + 1,
			height = (int)Math.round((KYMAX_Y - KYMIN_Y)/calibration) + 1;
		
		ImagePlus traceImp = IJ.createImage("Ori Image", width, height, tMax+1, 8);
		traceImp.setCalibration(imp.getCalibration());
		
		//save positions		
		ending = "_ori";
		if(encoding == MEDIANZ){
			ending += "ZCmedian";
		}else if(encoding == MEANZ){
			ending += "ZCmean";
		}else if(encoding == PUREZ){
			ending += "ZC";
		}
		
		try{
			double [][][] saveImage = new double [width][height][2];
			double intensity;
			int cx, cy;
			for(int i = 0; i < traces.size(); i++){
				if(!(traces.get(i).getTracePoints().size() > 0)) continue;
				if(!traces.get(i).oriented)	continue;
				
				for(int x = 0; x < width; x++){
					for(int y = 0; y < height; y++){
							saveImage [x][y][0] = 0.0;
							saveImage [x][y][1] = 0.0;
					}
				}
				
				for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
					intensity = tools.getEncodedIntensity8bit(traces.get(i).getTracePoints().get(j).getZ(encoding),KYMIN_Z,KYMAX_Z);
					if(traces.get(i).getTracePoints().get(j).getZ(encoding) == 0.0)	intensity = 255.0;
					cx = (int)Math.round((traces.get(i).getTracePoints().get(j).get2DOrientedVector()[0]-KYMIN_X)/calibration);
					cy = (int)Math.round((traces.get(i).getTracePoints().get(j).get2DOrientedVector()[1]-KYMIN_Y)/calibration);
					if(cx >= 0 && cx < saveImage.length && cy >= 0 && cy < saveImage[0].length){
						saveImage [cx][cy][0] += intensity;
						saveImage [cx][cy][1] += 1.0;
					}					
				}
				
				//write to image			
				for(int x = 0; x < width; x++){
					for(int y = 0; y < height; y++){
						if(saveImage[x][y][1] > 0.0){
							traceImp.getStack().setVoxel(x, y, traces.get(i).getFrame(), saveImage[x][y][0] / saveImage[x][y][1]); 
						}
					}
				
				}
			}
		}catch(Exception e){
//			IJ.log("error w" + width + " h" + height);
		}
						
		IJ.run(traceImp, "Fire", "");		
		IJ.saveAsTiff(traceImp, path + ending + ".tif");
		traceImp.changes = false;
		traceImp.close();
		
		//save image metadata
		TextPanel tp = new TextPanel("Metadata");
		tp.append("Image information for image:	" + path.substring(path.lastIndexOf(System.getProperty("file.separator"))) + ending + ".tif");
		tp.append("");
		tp.append("dimension		minimum	maximum");
		tp.append("gray value (1.0-254.0):	z information [um fit width]	" + constants.df6US.format(KYMIN_Z) + "	" + constants.df6US.format(KYMAX_Z));
		tp.append("");
		tp.append("code#"+KYMIN_Z+"-"+KYMAX_Z+"");
		addFooter(tp);
	  	tp.saveAs(path + ending + "_info.txt");
	}
	
	public static void saveOrientedKymograph(ArrayList<trace2D> traces, double calibration, String path, int encoding, int kymoType, int excludeHeadPoints){
		double min = getKymographMin(kymoType), max = getKymographMax(kymoType);		
		String xyzText = getKymographTxtLabel(kymoType);		
		
		//TODO test function
		if(min == 0.0 && max == 0.0)	IJ.log("minmax0 at kymoType" + kymoType);
		
		//get arc min max and tmax
			double arcLengthMin = Double.POSITIVE_INFINITY,
					arcLengthMax = Double.NEGATIVE_INFINITY;
			int	tMax = 0;
			for(int i = 0; i < traces.size(); i++){
				if(!traces.get(i).oriented)	continue;
				if(traces.get(i).getTracePoints().size() <= excludeHeadPoints)	continue;	
				if(traces.get(i).getFrame()>tMax)	tMax = traces.get(i).getFrame();
				
				for(int j = excludeHeadPoints; j < traces.get(i).getTracePoints().size(); j++){
					if(traces.get(i).getTracePoints().get(j).getArcLengthPos() > arcLengthMax){
						arcLengthMax = traces.get(i).getTracePoints().get(j).getArcLengthPos();
					}
					if(traces.get(i).getTracePoints().get(j).getArcLengthPos() < arcLengthMin){
						arcLengthMin = traces.get(i).getTracePoints().get(j).getArcLengthPos();
					}					
				}
			}			
			
		//generate image
			ImagePlus codeImp = IJ.createImage("Kymograph", (int)Math.round(arcLengthMax / calibration)+1, tMax+1, 1, 16);
			double values [][] = new double [(int)Math.round(arcLengthMax / calibration)+1][2];			
			
			for(int i = 0; i < traces.size(); i++){
				if(!traces.get(i).oriented)	continue;
				if(traces.get(i).getTracePoints().size() <= excludeHeadPoints)	continue;	
				// && Double.isNaN(traces.get(i).getThetaDegree(encoding))==false
				
				for(int a = 0; a < (int)Math.round(arcLengthMax / calibration)+1; a++){
					values [a][0] = 0.0;
					values [a][1] = 0.0;
				}
				
				for(int j = excludeHeadPoints; j < traces.get(i).getTracePoints().size(); j++){
					try{
						values[(int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos() / calibration)][0]
								+= tools.getEncodedIntensity16bit (getKymoTypeParameter(traces.get(i).getTracePoints().get(j), kymoType, encoding), min, max);
						values[(int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos() / calibration)][1] += 1.0;											
					}catch (Exception e){
						String out = "";
						for(int err = 0; err < e.getStackTrace().length; err++){
							out += " \n " + e.getStackTrace()[err].toString();
						}
						IJ.log("T" + i + ": problem in kymograph generation..." + " j" + j + " - " + (int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos() / calibration) 
						+ " length " + values.length + ". Error: " + out);
					}
				}	
				
				for(int a = (int)Math.round(arcLengthMin / calibration); a < (int)Math.round(arcLengthMax / calibration)+1; a++){
//							IJ.log("j " + j + " i " + i + "xyz" + xyz + " enc " + encoding);
//							IJ.log("al " + points.get(j).getArcLengthPos());
//							IJ.log("al " + (int)Math.round(points.get(j).getArcLengthPos() * resolution));
//							IJ.log("corrI " + getCorrectedIntensity8bit (points.get(j).getOrientedVector(encoding)[xyz], min, max));					
					if(values[a][1]>0.0){
						codeImp.getStack().setVoxel(a, traces.get(i).getFrame(), 0, values[a][0]/values[a][1]);
					}
				}								
			}
			
			IJ.run(codeImp, "Fire", "");			
			if(kymoType == KYMOY || kymoType == KYMOCANGLEXY){
//				IJ.run(codeImp, "Phase", "");
			}else{
				impProcessing.setOptimalDisplayRange(codeImp, false);
			}
			
			codeImp.getCalibration().pixelWidth = calibration;

			String ending = "_k" + xyzText;
			if((kymoType == KYMOZ || kymoType == KYMODZ)  && encoding==MEDIANZ){
				 ending += "medi";
			}else if((kymoType == KYMOZ || kymoType == KYMODZ) && encoding==MEANZ){
				ending += "mean";
			}
			
			IJ.saveAsTiff(codeImp, path + ending + ".tif");
			codeImp.changes = false;
			codeImp.close();
			
		//save image metadata
			TextPanel tp = new TextPanel("Metadata");
			tp.append("Image information for image:	" + path.substring(path.lastIndexOf(System.getProperty("file.separator"))) + ending + ".tif");
			tp.append("");
			tp.append("dimension		minimum	maximum");
			tp.append("x axis:	2D arc length	0	" + constants.df0.format((int)Math.round(arcLengthMax/calibration)+1) + "	calibration [um/px]: " + constants.df6US.format(calibration) + "");
			tp.append("y axis:	time [frame]	0	" + constants.df0.format(tMax));
			tp.append("gray value (1.0-65534.0):	oriented " + xyzText + " position	" + constants.df6US.format(min) + "	" + constants.df6US.format(max));
			tp.append("");
			tp.append("code#"+min+"-"+max+"+"+(1.0/calibration));	
			addFooter(tp);
		  	tp.saveAs(path + ending + "_info.txt");
	}
	
	/**
	 * @return kymograph minimum displayed value
	 * */
	private static double getKymographMin(int kymoType){
		switch(kymoType){
		case KYMOX:
			return KYMIN_X;
		case KYMOXRAW:
			return KYMIN_X;
		case KYMOY:
			return KYMIN_Y;
		case KYMOYRAW:
			return KYMIN_Y;
		case KYMOZ:
			return KYMIN_Z;
		case KYMOMAXINTENSITY:
			return KYMIN_MAXINTENSITY;
		case KYMOCURV:
			return KYMIN_CURV;
		case KYMOCANGLEXY:
			return KYMIN_DANGLEXY;
		case KYMODZ:
			return KYMIN_DZ;
		case KYMOTANGENTANGLE:
			return KYMIN_DANGLEXY;
		default:
			return -1000.0;
		}
	}
	
	/**
	 * @return kymograph maximum displayed value
	 * */
	private static double getKymographMax(int kymoType){
		switch(kymoType){
		case KYMOX:
			return KYMAX_X;
		case KYMOXRAW:
			return KYMAX_X;
		case KYMOY:
			return KYMAX_Y;
		case KYMOYRAW:
			return KYMAX_Y;
		case KYMOZ:
			return KYMAX_Z;
		case KYMOMAXINTENSITY:
			return KYMAX_MAXINTENSITY;
		case KYMOCURV:
			return KYMAX_CURV;
		case KYMOCANGLEXY:
			return KYMAX_DANGLEXY;
		case KYMODZ:
			return KYMAX_DZ;
		case KYMOTANGENTANGLE:
			return KYMIN_DANGLEXY;
		default:
			return -1000.0;
		}
	}
	
	/**
	 * @return label of displayed values
	 * */
	private static String getKymographTxtLabel(int kymoType){
		switch(kymoType){
		case KYMOX:
			return "X";
		case KYMOXRAW:
			return "XRaw";
		case KYMOY:
			return "Y";
		case KYMOYRAW:
			return "YRaw";
		case KYMOZ:
			return "Z";
		case KYMOMAXINTENSITY:
			return "MaxI";
		case KYMOCURV:
			return "Curv";
		case KYMOCANGLEXY:
			return "cAng";
		case KYMODZ:
			return "dZ";
		case KYMOTANGENTANGLE:
			return "tAng";
		default:
			return "";
		}
	}
	
	public static double getKymoTypeParameter (trackPoint2D p, int type, int encoding){
		switch(type){
			case KYMOX: return p.get2DOrientedVector()[0]; 
			case KYMOXRAW: return p.getX(); 
			case KYMOY: return p.get2DOrientedVector()[1];
			case KYMOYRAW: return p.getY(); 
			case KYMOZ: return p.getZ(encoding); 
			case KYMOMAXINTENSITY: return p.getNormalMaxIntensity(); 
			case KYMOCURV: return p.getCurvatureFactor();
			case KYMOCANGLEXY: return p.getDAngle2D();
			case KYMODZ: return p.getDZ(encoding);
			case KYMOTANGENTANGLE: return Math.toDegrees(tools.get2DAngle(p.getVector(), constants.X_AXIS));
			default: return 0.0;
		}
	}
	
	public static void saveOrientedKymographAsText(ArrayList<trace2D> traces, double calibration, String path, int encoding, int kymoType, int excludeHeadPoints){
		double min = getKymographMin(kymoType), max = getKymographMax(kymoType);		
		String xyzText = getKymographTxtLabel(kymoType);		
		String ending = "_k" + xyzText;
		if((kymoType == KYMOZ || kymoType == KYMODZ)  && encoding==MEDIANZ){
			 ending += "medi";
		}else if((kymoType == KYMOZ || kymoType == KYMODZ) && encoding==MEANZ){
			ending += "mean";
		}
		
		//TODO test function
//		if(min == 0.0 && max == 0.0)	IJ.log("minmax0 at kymoType" + kymoType);
		
		//get arc min max and tmax
			double arcLengthMin = Double.POSITIVE_INFINITY,
					arcLengthMax = Double.NEGATIVE_INFINITY;
			int	tMax = 0;
			for(int i = 0; i < traces.size(); i++){
				if(!traces.get(i).oriented)	continue;
				if(traces.get(i).getTracePoints().size() <= excludeHeadPoints)	continue;	
				if(traces.get(i).getFrame()>tMax)	tMax = traces.get(i).getFrame();
				
				for(int j = excludeHeadPoints; j < traces.get(i).getTracePoints().size(); j++){
					if(traces.get(i).getTracePoints().get(j).getArcLengthPos() > arcLengthMax){
						arcLengthMax = traces.get(i).getTracePoints().get(j).getArcLengthPos();
					}
					if(traces.get(i).getTracePoints().get(j).getArcLengthPos() < arcLengthMin){
						arcLengthMin = traces.get(i).getTracePoints().get(j).getArcLengthPos();
					}					
				}
			}			
			
		//generate table
			
			
			TextPanel tp = new TextPanel("Kymograph for the parameter " + xyzText);
			tp.append("Kymograph");
			tp.append("");
//			tp.append("dimension		minimum	maximum");
//			tp.append("x axis:	2D arc length	0	" + constants.df0.format((int)Math.round(arcLengthMax/calibration)+1) + "	calibration [um/px]: " + constants.df6US.format(calibration) + "");
//			tp.append("y axis:	time [frame]	0	" + constants.df0.format(tMax));
//			tp.append("gray value (1.0-65534.0):	oriented " + xyzText + " position	" + constants.df6US.format(min) + "	" + constants.df6US.format(max));
//			tp.append("");
//			tp.append("code#"+min+"-"+max+"+"+(1.0/calibration));	
			
			String appText = "frame";
			for(int a = 0; a < (int)Math.round(arcLengthMax / calibration)+1; a++){
				appText += "	" + constants.df6US.format((double)a * calibration);
			}
			tp.append(appText);

			double values [][] = new double [(int)Math.round(arcLengthMax / calibration)+1][2];			
			for(int i = 0; i < traces.size(); i++){
				appText = "" + constants.df0.format(traces.get(i).getFrame());
				if(!traces.get(i).oriented){
					tp.append(appText);
					continue;
				}
				if(traces.get(i).getTracePoints().size() <= excludeHeadPoints){
					tp.append(appText);
					continue;	
				}
				// && Double.isNaN(traces.get(i).getThetaDegree(encoding))==false
				for(int a = 0; a < (int)Math.round(arcLengthMax / calibration)+1; a++){
					values [a][0] = 0.0;
					values [a][1] = 0.0;
				}
								
				for(int j = excludeHeadPoints; j < traces.get(i).getTracePoints().size(); j++){
					try{
						values[(int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos() / calibration)][0]
								+= getKymoTypeParameter(traces.get(i).getTracePoints().get(j), kymoType, encoding);
						values[(int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos() / calibration)][1] += 1.0;											
					}catch (Exception e){
						String out = "";
						for(int err = 0; err < e.getStackTrace().length; err++){
							out += " \n " + e.getStackTrace()[err].toString();
						}
						IJ.log("T" + i + ": problem in kymograph generation..." + " j" + j + " - " + (int)Math.round(traces.get(i).getTracePoints().get(j).getArcLengthPos() / calibration) 
						+ " length " + values.length + ". Error: " + out);
					}
				}	
				
				//TODO
				for(int a = 0; a < (int)Math.round(arcLengthMax / calibration)+1; a++){
//							IJ.log("j " + j + " i " + i + "xyz" + xyz + " enc " + encoding);
//							IJ.log("al " + points.get(j).getArcLengthPos());
//							IJ.log("al " + (int)Math.round(points.get(j).getArcLengthPos() * resolution));
//							IJ.log("corrI " + getCorrectedIntensity8bit (points.get(j).getOrientedVector(encoding)[xyz], min, max));					
					appText += "	";
					if(values[a][1]>0.0){
						appText += constants.df6US.format(values[a][0]/values[a][1]);
					}
				}	
				tp.append(appText);
			}
		
			addFooter(tp);
		  	tp.saveAs(path + ending + ".txt");
	}
	
	public static void saveHeadRotationMatrixImage(ArrayList<trace2D> traces, double calibration, String path){
		//find tMax
		int tMax = 0;		
		for(int i = 0; i < traces.size(); i++){
			if(!traces.get(i).oriented)	continue;
			if(!traces.get(i).hrVset)	continue;			
			if(tMax < traces.get(i).getFrame())	tMax = traces.get(i).getFrame();			
		}
		
		double [] matrix = new double [41];
		ImagePlus codeImp = IJ.createImage("HRimage", matrix.length, 1, tMax+1, 16);
		for(int i = 0; i < traces.size(); i++){
			if(!traces.get(i).oriented)	continue;
			if(!traces.get(i).hrVset)	continue;
			
			matrix = traces.get(i).getHeadRotationMatrix();
			for(int x = 0; x < matrix.length; x++){
					codeImp.getStack().setVoxel(x, 0, traces.get(i).getFrame(), tools.getEncodedIntensity16bit(matrix [x], KYMIN_MAXINTENSITY, KYMAX_MAXINTENSITY));
			}			
		}
		
		impProcessing.setOptimalDisplayRange(codeImp, true);
		IJ.run(codeImp, "Fire", "");
		IJ.saveAsTiff(codeImp, path + "HRI.tif");
		codeImp.changes = false;
		codeImp.close();
		
		//save image metadata
		TextPanel tp = new TextPanel("Metadata");
		tp.append("Image information for image:	" + path.substring(path.lastIndexOf(System.getProperty("file.separator"))) + "HRI.tif");
		tp.append("");
		tp.append("dimension		minimum	maximum");
		tp.append("x axis:	points normal to orientation vector	0	9	calibration [um/px]: " + constants.df6US.format(calibration) + "");
		tp.append("y axis:	slice	0	4");
		tp.append("gray value (1.0-65534.0):	interpolated intensity	" + constants.df6US.format(KYMIN_MAXINTENSITY) + "	" + constants.df6US.format(KYMAX_MAXINTENSITY));
		tp.append("");
		tp.append("code#"+KYMIN_MAXINTENSITY+"-"+KYMAX_MAXINTENSITY+"+"+(1.0/calibration));	
		addFooter(tp);
	  	tp.saveAs(path + "HRI_info.txt");
		
	}
	
	public static double [][] getAndSaveFrequencies(ArrayList<trace2D> traces, String path, double calibration, int groupedTimesteps, int kymoType, int encoding,
			double sampleRate, double neglectedInitialArclength, double speciesLength){
		
		//get maximum arcLength and time-step
			double length = 0.0;
			int tMax = 0;
			for(int i = 0; i < traces.size(); i++){				
				if(traces.get(i).getFrame() > tMax)	tMax = traces.get(i).getFrame();
				if(traces.get(i).getTracePoints().size() > 0 && traces.get(i).oriented){
					for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
						if(traces.get(i).getTracePoints().get(j).getArcLengthPos() > length){
							length = traces.get(i).getTracePoints().get(j).getArcLengthPos();
						}
					}					
				}
			}
						
		//create Image
			int width = (int)Math.round(length / calibration)+1;
			int neglectedWidth = (int)Math.round(neglectedInitialArclength / calibration);
			if(neglectedInitialArclength <= 0.0)	neglectedWidth = -1;
			
		//initialize
			double [] medianVs = new double [] {0.0,0.0,0.0};
			int counter;
			
			double values [][][] = new double [width][2][groupedTimesteps];
			double valueColumn [] = new double [groupedTimesteps];
			double frequencies [][][] = new double [width][tMax + 1 - groupedTimesteps + 1][5];
			int calArcLPos = 0;
			int countValues;
			
			for(int orT = 0; orT < traces.size() && traces.get(orT).getFrame() <= tMax + 1 - groupedTimesteps; orT++){
				for(int t = 0; t < groupedTimesteps; t++){
					for(int a = neglectedWidth + 1; a < width; a++){
						values [a][0][t] = 0.0;
						values [a][1][t] = 0.0;
					}					
				}	
				
			
				//align
				for(int i = orT; i < traces.size() && traces.get(i).getFrame() < traces.get(orT).getFrame() + groupedTimesteps; i++){
					for(int j = 0; j < traces.get(i).getTracePoints().size(); j++){
						calArcLPos = (int)(traces.get(i).getTracePoints().get(j).getArcLengthPos()/calibration);
						values [calArcLPos][0][traces.get(i).getFrame()-traces.get(orT).getFrame()] 
								+= getKymoTypeParameter(traces.get(i).getTracePoints().get(j), kymoType, encoding);
						values [calArcLPos][1][traces.get(i).getFrame()-traces.get(orT).getFrame()] += 1.0;
						if(calArcLPos+1 < width){
							values [calArcLPos+1][0][traces.get(i).getFrame()-traces.get(orT).getFrame()] 
									+= getKymoTypeParameter(traces.get(i).getTracePoints().get(j), kymoType, encoding);
							values [calArcLPos+1][1][traces.get(i).getFrame()-traces.get(orT).getFrame()] += 1.0;
						}
						if(calArcLPos-1 >= 0){
							values [calArcLPos-1][0][traces.get(i).getFrame()-traces.get(orT).getFrame()] 
									+= getKymoTypeParameter(traces.get(i).getTracePoints().get(j), kymoType, encoding);
							values [calArcLPos-1][1][traces.get(i).getFrame()-traces.get(orT).getFrame()] += 1.0;
						}
						
					}
				}
				
				//calculate values
				for(int t = 0; t < groupedTimesteps; t++){
					for(int a = neglectedWidth + 1; a < width; a++){
						if(values [a][1][t] != 0.0){
							values [a][0][t] /= values [a][1][t];
						}
					}
				}
								
				//get frequencies
				for(int a = neglectedWidth + 1; a < width; a++){
//					if(a == (int)(width*3.0/4.0)){
//						showAsPlot(values [a][0]);
//						IJ.log("arclpos " + a + " = " + (a*calibration));
//					}
					//get smoothed graph
					Arrays.fill(valueColumn, Double.NaN);
					countValues = 0;
					for(int t = 0; t < groupedTimesteps; t++){
						if(values [a][1][t] != 0.0){
							countValues ++;
							valueColumn [t] = values [a][0][t];
							/**
							 * Smooth - removed from version v0.1.8 on
							 * */
//							counter = 1;							
//							medianVs [0] = values [a][0][t];
//							if(t + 1 < groupedTimesteps
//									&& values [a][1][t+1] != 0.0){
//								medianVs [counter] = values [a][0][t+1];
//								counter++;
//							}
//							if(t != 0
//									&& values [a][1][t-1] != 0.0){
//								medianVs [counter] = values [a][0][t-1];
//								counter++;
//							}
//							if(counter==1){
//								valueColumn [t] = medianVs [0];
//							}else if(counter == 2){
//								valueColumn [t] = (medianVs [0] + medianVs [1]) / 2.0;
//							}else{
//								valueColumn [t] = tools.getMedian(medianVs);
//							}							
						}
						/**
						 * Fill holes (needed as FFT does not perform with gaps)
						 * */							
						else{
							//fill holes in graph
							valueColumn [t] = 0.0;
							fillingHoles: for(int l = 1; l < 10; l++){
								if(t + l < values[a][0].length){
									valueColumn [t] = values [a][0][t+l];
								}
								if(t - l >= 0){
									if(valueColumn [t] != 0.0){
										valueColumn [t] += values [a][0][t-l];
										valueColumn [t] /= 2.0;
									}else{
										valueColumn [t] = values [a][0][t-l];
									}
								}
								if(valueColumn [t] != 0.0){
									break fillingHoles;
								}
							}
						}
					}
					if((double)countValues/(double)valueColumn.length>=main.threshold){
						if(kymoType == tools2D.KYMOCURV || kymoType == tools2D.KYMOZ || kymoType == tools2D.KYMOMAXINTENSITY || kymoType == tools2D.KYMOX){
							frequencies [a][traces.get(orT).getFrame()] = getFrequenciesAndAmplitude(valueColumn, sampleRate, false, true);
						}else{
							frequencies [a][traces.get(orT).getFrame()] = getFrequenciesAndAmplitude(valueColumn, sampleRate, false, false);
						}
					}else{
						for(int f = 0; f < frequencies [a][traces.get(orT).getFrame()].length; f++){
							frequencies [a][traces.get(orT).getFrame()][f] = 0.0;
						}
					}
										
					//display frequency
//					if(a == (int)(width*3.0/4.0)){
//						showAsPlot(valueColumn);
//						frequencies [a][traces.get(orT).getFrame()] = getFrequency(valueColumn, sampleRate, true);
//					}
				}
			}
			
			//Median interpolation of Freqs and Amplitudes along flagellum
			medianVs = new double [5];			
			for(int t = 0; t < tMax + 1 - groupedTimesteps + 1; t++){
				for(int a = neglectedWidth+1; a < width; a++){
					for(int f = 0; f < 5; f++){
						if(frequencies [a][t][f] > 0.0){
							counter = 0;
							for(int add = a - 2; add <= a + 2; add++){
								if(add < width 
										&& add > neglectedWidth 
										&& frequencies [add][t][f] > 0.0){
									medianVs [counter] = frequencies [add][t][f];
									counter++;
								}
							}
							if(counter==1){
								frequencies [a][t][f] = medianVs [0];
							}else if(counter>1){
								frequencies [a][t][f] = tools.getMedianOfRange(medianVs, 0, counter-1);
							}						
//						}else{
//							IJ.log(getKymographTxtLabel(kymoType) + ": " + a + "-" + t + "-" + f + ": " + frequencies [a][t][f]);
						}
					}
					
					
				}
			}
			
			double [][] freqs = new double [3][3];
			int [][] freqsCt = new int [3][3];
		  	for(int f1 = 0; f1 < 3; f1++){
		  		freqs [0][f1] = 0.0;
		  		freqs [1][f1] = 0.0;
		  		freqs [2][f1] = 0.0;
		  		freqsCt [0][f1] = 0;
		  		freqsCt [1][f1] = 0;
		  		freqsCt [2][f1] = 0;
		  	}
		  	
		  	int maxSpeciesLength = (int)Math.round(speciesLength/calibration)+1;
			for(int a = neglectedWidth+1; a < maxSpeciesLength && a < frequencies.length; a++){
				for(int t = 0; t < tMax + 1 - groupedTimesteps + 1; t++){
					if(frequencies [a][t][0] > 0.0){						
						freqs [0][(int)((a/(double)maxSpeciesLength)*3.0)] += frequencies [a][t][0];
						freqsCt [0][(int)((a/(double)maxSpeciesLength)*3.0)] ++;
						freqs [1][(int)((a/(double)maxSpeciesLength)*3.0)] += frequencies [a][t][2];
						freqsCt [1][(int)((a/(double)maxSpeciesLength)*3.0)] ++;
						freqs [2][(int)((a/(double)maxSpeciesLength)*3.0)] += frequencies [a][t][4];
						freqsCt [2][(int)((a/(double)maxSpeciesLength)*3.0)] ++;
					}					
				}
			}

		//save freqs as image
			ImagePlus codeImpF = IJ.createImage("Frequency graph", width, tMax + 1 - groupedTimesteps + 1, 5, 16);
			double amplMin = KYMIN_AMPLITUDE, amplMax = KYMAX_AMPLITUDE;
			if(kymoType == tools2D.KYMOCURV ||kymoType == tools2D.KYMODZ ||kymoType == tools2D.KYMOZ){
				amplMin = KYMIN_AMPLITUDESMALLPARAM;
				amplMax = KYMAX_AMPLITUDESMALLPARAM;
			}
			for(int a = neglectedWidth+1; a < width; a++){
				for(int t = 0; t < tMax + 1 - groupedTimesteps + 1; t++){
					if(frequencies [a][t][0] > 0.0){
						codeImpF.getStack().setVoxel(a, t, 0, tools.getEncodedIntensity16bit(frequencies [a][t][0], KYMIN_FREQ, KYMAX_FREQ));
					}					
					if(frequencies [a][t][1] > 0.0){
						codeImpF.getStack().setVoxel(a, t, 1, tools.getEncodedIntensity16bit(frequencies [a][t][1], amplMin, amplMax));
					}
					if(frequencies [a][t][2] > 0.0){
						codeImpF.getStack().setVoxel(a, t, 2, tools.getEncodedIntensity16bit(frequencies [a][t][2], KYMIN_FREQ, KYMAX_FREQ));
					}
					if(frequencies [a][t][3] > 0.0){
						codeImpF.getStack().setVoxel(a, t, 3, tools.getEncodedIntensity16bit(frequencies [a][t][3], amplMin, amplMax));
					}
					if(frequencies [a][t][4] > 0.0){
						codeImpF.getStack().setVoxel(a, t, 4, tools.getEncodedIntensity16bit(frequencies [a][t][4], KYMIN_FREQ, KYMAX_FREQ));
					}
				}
			}
			codeImpF.getCalibration().pixelWidth = calibration;
			IJ.run(codeImpF, "Fire", "");
			
			IJ.saveAsTiff(codeImpF, path + getKymographTxtLabel(kymoType) + "_f.tif");
			codeImpF.changes = false;
			codeImpF.close();
			
		//save freqs as text
			TextPanel tp;			
			for(int f = 0; f < 5; f++){
				tp = new TextPanel("Frequency results for the parameter " + getKymographTxtLabel(kymoType) + "_" + (f+1));
				tp.append("Frequency results for the parameter " + getKymographTxtLabel(kymoType) + ": " + FREQUENCYPARAMETERASSTRING[f]);
				tp.append("");
				
				String appText = "frame group" + "	" + "arc length (µm)";
				tp.append(appText);
				
				appText = "";
				for(int a = 0; a < width; a++){
					appText += "	" + constants.df6US.format((double)a * calibration);
				}
				tp.append(appText);
				
				for(int t = 0; t < tMax + 1 - groupedTimesteps + 1; t++){
					appText = "" + constants.df0.format(t) + "-" + constants.df0.format(t+groupedTimesteps);
					for(int a = 0; a < width; a++){
						appText += "	";
						if(a >= neglectedWidth+1){
							if(frequencies [a][t][f] > 0.0){
								appText += constants.df6US.format(frequencies [a][t][f]);
							}							
						}
					}
					tp.append(appText);
				}				
				addFooter(tp);
			  	tp.saveAs(path + getKymographTxtLabel(kymoType) + "_f_" + (f+1) + ".txt");
			}			
		  	
		//save image metadata
			tp = new TextPanel("Metadata");
			tp.append("Image information for image:	" + path.substring(path.lastIndexOf(System.getProperty("file.separator"))) + "_f.tif");
			tp.append("");
			tp.append("dimension		minimum	maximum");
			tp.append("x axis:	xy arc length	0	" + constants.df0.format((int)Math.round(length/calibration)+1) + "	calibration: " + constants.df6US.format(calibration) + "");
			tp.append("y axis:	time [frame]	0 - " + constants.df0.format(groupedTimesteps) 
				+ "	" + constants.df0.format(tMax-1 - groupedTimesteps) + " - " + constants.df0.format(tMax-1));
			tp.append("z axis:	0,2 = 1st and 2nd largest frequency in FFT	1,3 = FFT amplitudes of 1st and 2nd largest frequency	4 = com frequence of FFT");
			tp.append("gray value of z=0,2 (1.0-65534.0):	frequency	" + constants.df6US.format(KYMIN_FREQ) + "	" + constants.df6US.format(KYMAX_FREQ));
			tp.append("gray value of z=1,3 (1.0-65534.0):	amplitude	" + constants.df6US.format(amplMin) + "	" + constants.df6US.format(amplMax));
			tp.append("");
			tp.append("code#"+KYMIN_FREQ+"&"+KYMAX_FREQ+"*"+amplMin+"!"+amplMax+"+"+calibration);
			addFooter(tp);
			tp.saveAs(path + "_"+ getKymographTxtLabel(kymoType) + "_f_info.txt");
		  	
		//calculate frequencies by group
		  	for(int f1 = 0; f1 < 3; f1++){
		  		freqs [0][f1] /= (double) freqsCt [0][f1];
		  		freqs [1][f1] /= (double) freqsCt [1][f1];
		  		freqs [2][f1] /= (double) freqsCt [2][f1];
		  	}
		  	return freqs;
		  	
	}
	
	/**
	 * @deprecated
	 * */
	public static double getFrequency (double [] values, double sampleRate, boolean showPlot){
		DoubleFFT_1D fftDo = new DoubleFFT_1D(values.length);	//Package unter: http://incanter.org/docs/parallelcolt/api/edu/emory/mathcs/jtransforms/fft/DoubleFFT_1D.html
        double [] fft = new double[values.length * 2];
        double [] magnitude = new double[values.length];
        System.arraycopy(values, 0, fft, 0, values.length); 
                       fftDo.realForwardFull(fft);
                       
        double real, imaginary;
        for(int j = 0; j < values.length; j++){
        	real = fft[2*j];
        	imaginary = fft[2*j+1];
        	magnitude [j] = Math.sqrt(real*real+imaginary*imaginary);
        }
        
        //display as plot
        if(showPlot)	tools.showAsPlot(magnitude);
        
        //output maximum frequency found (from index 2 on)    	
        return (tools.getMaximumIndexWithinRange(magnitude, 2, (magnitude.length/2)) * sampleRate / magnitude.length) ;
	}
	
	/**
	 * @performs a 1D frequency analysis of the array @param values
	 * @param showPlot: if true displays a plot of the found FFT
	 * @returns an array containing {highest freq. peak, amplitude of highest freq. peak,
	 * 								 2nd highest freq. peak, amplitude of 2nd highest freq. peak}
	 * */
	public static double [] getFrequenciesAndAmplitude (double [] values, double sampleRate, boolean showPlot, boolean normalizePlusMinus){
		//DoubleFFT package from: http://incanter.org/docs/parallelcolt/api/edu/emory/mathcs/jtransforms/fft/DoubleFFT_1D.html
		DoubleFFT_1D fftDo = new DoubleFFT_1D(values.length);	
        double [] fft = new double[values.length * 2];
        double [] magnitude = new double[values.length];
        System.arraycopy(values, 0, fft, 0, values.length); 
        
        //normalization of values to +/- range
      		if(normalizePlusMinus && tools.getMinimumWithinRange(fft, 0, fft.length-1) >= 0.0){
      			double max = tools.getMaximum(fft);
      			for(int i = 0; i < fft.length; i++){
      				fft [i] -= max;
      			}
      		}
        
        fftDo.realForwardFull(fft);
                       
        double real, imaginary;
        for(int j = 0; j < values.length; j++){
        	real = fft[2*j];
        	imaginary = fft[2*j+1];
        	magnitude [j] = Math.sqrt(real*real+imaginary*imaginary);
        }
        
        //display as plot
        if(showPlot)	tools.showAsPlot(magnitude);
        
        //output maximum frequencies and amplitudes found (from index 2 on)
        int [] freqs = tools.get2HighestMaximaIndicesWithinRange(magnitude, (int)Math.round(2*(magnitude.length/sampleRate)), (magnitude.length/2));
        double [] ampl = new double [2];
        double com = tools.getCenterOfMassOfRange(magnitude, 0, (magnitude.length/2)-1);
        if(freqs[0] >= 0 && freqs [0] < magnitude.length){
        	ampl [0] = magnitude [freqs[0]];
        }else{
        	ampl [0] = 0.0;
        }
        if(freqs[1] >= 0 && freqs [1] < magnitude.length){
        	ampl [1] = magnitude [freqs[1]];
        }else{
        	ampl [1] = 0.0;
        }
        return new double [] {freqs [0] * sampleRate / magnitude.length, ampl [0],
	    		freqs [1] * sampleRate / magnitude.length, ampl [1], com * sampleRate / magnitude.length};
	}
	

	public static double getTraceTypeParameter (trace2D t, int type,
			double minIntensity, double maxIntensity, 
			double minPosition, double maxPosition){
		switch(type){
			case TRACE_THETA: 
				return t.getThetaDegree(NOZ);
			case TRACE_HRMAXPOSITION: 
				return t.getHeadRotationMaximumPositions();
			case TRACE_HRMAXINTENSITY: 
				return t.getHeadRotationMaximumIntensities();
			case TRACE_HRANGLE: 
				return t.getHeadRotationAngle(minIntensity, maxIntensity, minPosition, maxPosition);
			default: return 0.0;
		}
	}
	
	public static String getTraceTypeParameterText (int type){
		switch(type){
			case TRACE_THETA:
				return "Th2D";
			case TRACE_HRMAXPOSITION: 
				return "HRMaxPos";
			case TRACE_HRMAXINTENSITY: 
				return "HRMaxInt";
			case TRACE_HRANGLE: 
				return "HRAng";
			default: return null;
		}
	}
	
	public static double [] getAndSaveGroupedFrequenciesTraceParam (ArrayList<trace2D> traces, int traceParamType, 
			int groupedTimesteps, double sampleRate,
			String savePath,
			double minIntensity, double maxIntensity, 
			double minPosition, double maxPosition){
		
		//get max time-step
			int tMax = 0;
			for(int i = 0; i < traces.size(); i++){				
				if(traces.get(i).getFrame() > tMax)	tMax = traces.get(i).getFrame();
			}
			
		//initialize
			double [] medianVs = new double [] {0.0,0.0,0.0};
			int counter;
			
			double values [] = new double [groupedTimesteps];
			double valueColumn [] = new double [groupedTimesteps];
			double frequencies [][] = new double [tMax + 1 - groupedTimesteps + 1][5];
			for(int i = 0; i < frequencies.length; i++){
				Arrays.fill(frequencies [i], 0.0);
			}
			
			for(int orT = 0; orT < traces.size() && traces.get(orT).getFrame() <= tMax + 1 - groupedTimesteps; orT++){
				for(int t = 0; t < groupedTimesteps; t++){
					values [t] = Double.NEGATIVE_INFINITY;										
				}	
				
				//get values
				for(int i = orT; i < traces.size() && traces.get(i).getFrame() < traces.get(orT).getFrame() + groupedTimesteps; i++){
					values [traces.get(i).getFrame()-traces.get(orT).getFrame()] 
							= getTraceTypeParameter(traces.get(i), traceParamType, 
							minIntensity, maxIntensity, minPosition, maxPosition);
				}
								
				//get frequencies
					{
						//get smoothed graph
						for(int t = 0; t < groupedTimesteps; t++){
							if(values [t] != Double.NEGATIVE_INFINITY){
								counter = 1;
								medianVs [0] = values [t];
								if(t + 1 < groupedTimesteps 
										&& values [t+1] != Double.NEGATIVE_INFINITY){
									medianVs [counter] = values [t+1];
									counter++;
								}
								if(t != 0
										&& values [t-1] != Double.NEGATIVE_INFINITY){
									medianVs [counter] = values [t-1];
									counter++;
								}
								if(counter==1){
									valueColumn [t] = medianVs [0];
								}else if(counter == 2){
									valueColumn [t] = (medianVs [0] + medianVs [1]) / 2.0;
								}else{
									valueColumn [t] = tools.getMedian(medianVs);
								}
							}else{
								//fill holes in graph
								valueColumn [t] = 0.0;
								fillingHoles: for(int l = 1; l < 10; l++){
									if(t + l < values.length){
										valueColumn [t] = values [t+l];
									}
									if(t - l >= 0){
										if(valueColumn [t] != 0.0){
											valueColumn [t] += values [t-l];
											valueColumn [t] /= 2.0;
										}else{
											valueColumn [t] = values [t-l];
										}
									}
									if(valueColumn [t] != 0.0){
										break fillingHoles;
									}
								}
							}								
						}
					}						
//					if(traceParamType == tools2D.TRACE_HRANGLE){
						frequencies [traces.get(orT).getFrame()] = getFrequenciesAndAmplitude(valueColumn, sampleRate, false, true);
//					}else{
//						
//					}					
				}
			
			double [] freqs = new double [3];
			int [] freqsCt = new int [3];
	  		freqs [0] = 0.0;
	  		freqs [1] = 0.0;
	  		freqs [2] = 0.0;
	  		freqsCt [0] = 0;
	  		freqsCt [1] = 0;
	  		freqsCt [2] = 0;
	  		
			for(int t = 0; t < tMax + 1 - groupedTimesteps + 1; t++){
				if(frequencies [t][0] > 0.0){						
					freqs [0] += frequencies [t][0];
					freqsCt [0] ++;
					freqs [1] += frequencies [t][2];
					freqsCt [1] ++;
					freqs [2] += frequencies [t][4];
					freqsCt [2] ++;
				}					
			}
			
			TextPanel tp = new TextPanel("Freq Results");
			tp.append("Frequencies for trace parameter:	" + getTraceTypeParameterText(traceParamType));
			tp.append("Analyzed image:	" + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) + "_f.tif");
			tp.append("");
			tp.append("t [frame]" + "	" + "Primary freq." + "	" + "Amplitude of primary freq. peak in FFT"
					+ "	" + "Secondary freq." + "	" + "Amplitude of secondary freq. peak in FFT"
					+ "	" + "COM freq.");
			
			String txt;
			for(int t = 0; t < tMax + 1 - groupedTimesteps + 1; t++){
				txt = "" + constants.df0.format(t) + "-" + constants.df0.format(t+groupedTimesteps);
				txt += "	";
				if(frequencies [t][0] > 0.0){
					txt += "" + frequencies [t][0];
				}					
				txt += "	";
				if(frequencies [t][1] > 0.0){
					txt += "" + frequencies [t][1];
				}
				txt += "	";
				if(frequencies [t][2] > 0.0){
					txt += "" + frequencies [t][2];
				}
				txt += "	";
				if(frequencies [t][3] > 0.0){
					txt += "" + frequencies [t][3];
				}
				txt += "	";
				if(frequencies [t][4] > 0.0){
					txt += "" + frequencies [t][4];
				}
				tp.append(txt);
			}
			addFooter(tp);
		  	tp.saveAs(savePath + "_" + getTraceTypeParameterText(traceParamType) + "_f.txt");
		  	
		//calculate frequencies by group
	  		freqs [0] /= (double) freqsCt [0];
	  		freqs [1] /= (double) freqsCt [1];
	  		freqs [2] /= (double) freqsCt [2];
		  	return freqs;
	}
	
	static void addFooter (TextPanel tp){
		tp.append("");
		tp.append("Datafile was generated by '"+main.PLUGINNAME+"', (\u00a9 2016 - " + constants.dateY.format(new Date()) + ": Jan N Hansen (jan.hansen@uni-bonn.de.de) \u0026 Jan F Jikeli (jan.jikeli@caesar.de))");
		tp.append("The plug-in '"+main.PLUGINNAME+"' is distributed in the hope that it will be useful,"
				+ " but WITHOUT ANY WARRANTY; without even the implied warranty of"
				+ " MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");
		tp.append("Skeleton results are generated using the ImageJ plug-ins 'skeletonize' and 'analyze skeleton'"
		+ " by: Ignacio Arganda-Carreras, Rodrigo Fernandez-Gonzalez, Arrate Munoz-Barrutia, Carlos Ortiz-De-Solorzano,"
		+ " '3D reconstruction of histological sections: Application to mammary gland tissue', Microscopy Research and Technique,"
		+ " Volume 73, Issue 11, pages 1019-1029, October 2010.");
		tp.append("Plug-in version:	" + main.PLUGINVERSION);	  	
	}
	
	/**
	 * @return the distance between the two trackPoints, @param p and @param q
	 * @param encoding specifies which way of Z-calculation is used to calculate the distance
	 * */
	public static double getDistance (trackPoint2D p, trackPoint2D q, int encoding){
		if(p == null)	IJ.log("p=0");
		if(q == null)	IJ.log("q=0");
		if(Double.isNaN(p.getX()) ||Double.isNaN(p.getY())){
			IJ.log("px " + p.getX() + " py " + p.getY());
			
		}
		if(Double.isNaN(q.getX()) || Double.isNaN(q.getY()) ){
			IJ.log("qx " + q.getX() + " qy " + q.getY());
		}
		
		return Math.sqrt(Math.pow(p.getX()-q.getX(),2.0)+Math.pow(p.getY()-q.getY(),2.0)+Math.pow(p.getZ(encoding)-q.getZ(encoding),2.0));
	}
	
	/**
	 * @return the curvature of the trace section between two trackPoints, @param p and @param q
	 * The curvature is defined as |0.5 * (vector q - vector p)| / arclength distance
	 * Multiply by crossproduct / |crossproduct| to get a sign
	 * */
	public static double getCurvatureFactor(trackPoint2D p, trackPoint2D q){
		//TODO new 08.10.2018
		double alDistance = tools.mathAbs(p.getArcLengthPos()-q.getArcLengthPos());
		if(alDistance == 0.0)	return 0.0;
		double [] pVec = {p.getVector()[0],p.getVector()[1],0.0};
		double [] qVec = {q.getVector()[0],q.getVector()[1],0.0};		
		return Math.signum(tools.crossProduct(qVec, pVec)[2]) 
				* Math.sqrt(Math.pow(q.getVector()[0]-p.getVector()[0], 2)+Math.pow(q.getVector()[1]-p.getVector()[1], 2)) 
				/ 2.0 / alDistance;
	}
	
	public static double [] get2DVectorFromPoints (double [] values){
		double slope = 0.0;
		int counter = 0;
		for(int i = 0; i < values.length; i++){
			for(int j = i+1; j < values.length; j++){
				slope += (values [j] - values [i])/(j-i);
				counter ++;
			}
		}
		return new double [] {1.0, (slope/counter)};
	}

	
}
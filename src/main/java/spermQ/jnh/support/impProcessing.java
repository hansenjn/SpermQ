package spermQ.jnh.support;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.ImageConverter;

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

* */

public class impProcessing {

	public static double getInterpolatedIntensity2D (ImagePlus imp, double x, double y, int z){
			x /= imp.getCalibration().pixelWidth;
			y /= imp.getCalibration().pixelHeight;
			double intensity = 0.0;
			double leftComponent = 1-(x-(int)x), topComponent = 1-(y-(int)y);		
			intensity += imp.getStack().getVoxel((int)x,(int)y,z) * leftComponent * topComponent;		//top left pixel
			intensity += imp.getStack().getVoxel((int)x+1,(int)y,z) * (1.0-leftComponent) * topComponent;	//top right pixel
			intensity += imp.getStack().getVoxel((int)x,(int)y+1,z) * leftComponent * (1.0-topComponent);	//bottom left pixel
			intensity += imp.getStack().getVoxel((int)x+1,(int)y+1,z) * (1.0-leftComponent) * (1.0-topComponent);	//bottom right pixel		
			return intensity;		
		}

	public static void optimal8BitConversion (ImagePlus imp){
		//set displayrange from minimum to maximum and then convert		
		double min = Double.POSITIVE_INFINITY, max = 0.0;
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int z = 0; z < imp.getStackSize(); z++){
					if(imp.getStack().getVoxel(x, y, z)>max){
						max = imp.getStack().getVoxel(x, y, z);
					}
					if(imp.getStack().getVoxel(x, y, z)<min){
						min = imp.getStack().getVoxel(x, y, z);
					}
				}				
			}
		}

		imp.setDisplayRange(min, max);
		if(imp.getBitDepth() != 8){
			ImageConverter impConv = new ImageConverter(imp);
			impConv.convertToGray8();
		}		
	}
	
	public static void setOptimalDisplayRange (ImagePlus imp, boolean includeZero){
		//set displayrange from minimum to maximum and then convert		
		double min = Double.POSITIVE_INFINITY, max = 0.0;
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int z = 0; z < imp.getStackSize(); z++){
					if(imp.getStack().getVoxel(x, y, z)>max){
						max = imp.getStack().getVoxel(x, y, z);
					}
					if((includeZero || imp.getStack().getVoxel(x, y, z) != 0.0) && imp.getStack().getVoxel(x, y, z)<min){
						min = imp.getStack().getVoxel(x, y, z);
					}
				}				
			}
		}
		if(!includeZero){
			min -= 1.0;
			if(min < 0.0) min = 0.0;
		}
		imp.setDisplayRange(min, max);	
	}

	public static ImagePlus getSingleImageFromStack(ImagePlus imp, int i){
		/**
		 * Returns an independent ImagePlus of the selected stack image
		 * imp: image where single timepoint shall be derived from
		 * i: stack image 0 <= i < # stack images)
		 * */
		ImagePlus newImp = IJ.createImage("Z" + i, imp.getWidth(), imp.getHeight(), 1, imp.getBitDepth());
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				newImp.getStack().setVoxel(x, y, 0, imp.getStack().getVoxel(x, y, i));
			}
		}
		newImp.setCalibration(imp.getCalibration());
		return newImp;
	}

	public static ImagePlus getSharpestPlane(ImagePlus imp){
		/**
		 * Returns an independent ImagePlus of the sharpest image in the ImagePlus
		 * The sharpest image is defined as the image with the lowest Standard Deviation
		 * imp: stack-image where the sharpest image shall be found
		 * */		
		double [] SD = new double [imp.getStackSize()];
		double maximum = 0.0; int maximumPos = -1;
		
		double average = 0.0;
		int counter = 0;
		for(int z = 0; z < imp.getStackSize(); z++){
			average = 0.0;
			counter = 0;
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, z) != 0.0){
						average += imp.getStack().getVoxel(x, y, z);
						counter++;
					}
				}
			}	
			average /= counter;
			
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, z) != 0.0){
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
		
		return getSingleImageFromStack(imp, maximumPos);
	}

	public static ImagePlus getSharpestPlaneForSelection(ImagePlus imp, Roi roi){
		/**
		 * Returns an independent ImagePlus of the sharpest image in the ImagePlus
		 * The sharpest image is defined as the image with the lowest Standard Deviation
		 * imp: stack-image where the sharpest image shall be found
		 * roi: the region of interest in which the SD is calculated
		 * */		
		double [] SD = new double [imp.getStackSize()];
		double maximum = 0.0; int maximumPos = -1;
		
		double average = 0.0;
		int counter = 0;
		for(int z = 0; z < imp.getStackSize(); z++){
			average = 0.0;
			counter = 0;
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, z) != 0.0 
							&& roi.contains(x,y)){
						average += imp.getStack().getVoxel(x, y, z);
						counter++;
					}
				}
			}	
			average /= counter;
			
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, z)!=0.0 && roi.contains(x,y)){
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
		
		return getSingleImageFromStack(imp, maximumPos);
	}

	public static ImagePlus maxIntensityProjection(ImagePlus imp){
		ImagePlus impMax = IJ.createImage("maximum projection", imp.getWidth(), imp.getHeight(), 1, imp.getBitDepth());
		
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				impMax.getStack().setVoxel(x, y, 0, 0.0);
				for(int s = 0; s < imp.getStackSize(); s++){
					if(imp.getStack().getVoxel(x, y, s) > impMax.getStack().getVoxel(x, y, 0)){
						impMax.getStack().setVoxel(x, y, 0, imp.getStack().getVoxel(x, y, s));
					}
				}
			}
		}		
		
		impMax.setCalibration(imp.getCalibration());
		return impMax;
	}
	
	public static ImagePlus maxIntensityProjectionOfSlices(ImagePlus imp){
		ImagePlus impMax = IJ.createHyperStack("max s", imp.getWidth(), imp.getHeight(), imp.getNChannels(), 1, imp.getNFrames(), imp.getBitDepth());
		
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int c = 0; c < imp.getNChannels(); c++){
					for(int t = 0; t < imp.getNFrames(); t++){
						for(int s = 0; s < imp.getNSlices(); s++){
							if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c+1, s+1, t+1)-1) 
									> impMax.getStack().getVoxel(x, y, impMax.getStackIndex(c+1, 1, t+1)-1)){
								impMax.getStack().setVoxel(x, y, impMax.getStackIndex(c+1, 1, t+1)-1,
										imp.getStack().getVoxel(x, y, imp.getStackIndex(c+1, s+1, t+1)-1));
							}
						}						
					}
				}				
			}
		}		
		
		impMax.setCalibration(imp.getCalibration());
		return impMax;
	}
	
	public static ImagePlus maxIntensityProjectionOfTimesteps(ImagePlus imp){
		ImagePlus impMax = IJ.createHyperStack("max t", imp.getWidth(), imp.getHeight(), imp.getNChannels(), imp.getNSlices(), 1, imp.getBitDepth());
		
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				for(int c = 0; c < imp.getNChannels(); c++){
					for(int s = 0; s < imp.getNSlices(); s++){
						for(int t = 0; t < imp.getNFrames(); t++){
							if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c+1, s+1, t+1)-1) 
									> impMax.getStack().getVoxel(x, y, impMax.getStackIndex(c+1, s+1, 1)-1)){
								impMax.getStack().setVoxel(x, y, impMax.getStackIndex(c+1, s+1, 1)-1,
										imp.getStack().getVoxel(x, y, imp.getStackIndex(c+1, s+1, t+1)-1));
							}
						}						
					}
				}				
			}
		}		
		
		impMax.setCalibration(imp.getCalibration());
		return impMax;
	}

	public static ImagePlus averageIntensityProjection(ImagePlus imp){
		ImagePlus impMax = IJ.createImage("maximum projection", imp.getWidth(), imp.getHeight(), 1, imp.getBitDepth());
		double average;
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				average = 0.0;
				for(int s = 0; s < imp.getStackSize(); s++){
					average += imp.getStack().getVoxel(x, y, s);					
				}
				impMax.getStack().setVoxel(x, y, 0, (average / (double)imp.getStackSize()));
			}
		}			
		impMax.setCalibration(imp.getCalibration());
		return impMax;
	}
	
	public static ImagePlus stackSum(ImagePlus imp){
		ImagePlus impMax = IJ.createImage("maximum projection", imp.getWidth(), imp.getHeight(), 1, imp.getBitDepth());
		double sum;
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){
				sum = 0.0;
				for(int s = 0; s < imp.getStackSize(); s++){
					sum += imp.getStack().getVoxel(x, y, s);					
				}
				impMax.getStack().setVoxel(x, y, 0, sum);
			}
		}			
		impMax.setCalibration(imp.getCalibration());
		return impMax;
	}

	
	public static ImagePlus splitToStack(ImagePlus imp){
			final int channels = imp.getNChannels();
			int slices = imp.getNSlices();
			int frames = imp.getNFrames();
			
			final int width = imp.getWidth();
			final int height = imp.getHeight();
			int newWidth = (int)((double)width/2.0);
			int newHeight = (int)((double)height/2.0);
			int substractWidth = newWidth;
			int substractHeight = newHeight;		
			if(width%2==1)	substractWidth++;
			if(height%2==1)	substractHeight++;				
			ImageStack iS = imp.getStack();
			
	
			ImagePlus impNew = IJ.createHyperStack("hyperstack image v1", newWidth, newHeight, channels, 4, frames, imp.getBitDepth());
			
			if(frames > 1 && slices == 1){			
				for(int c = 0; c < channels; c++){
					for(int t = 0; t < frames; t++){
						for(int x = 0; x < newWidth; x++){
							for(int y = 0; y < newHeight; y++){
								int os = imp.getStackIndex(c+1, 1, t+1)-1;
															
								int ns = impNew.getStackIndex(c+1, 1, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x + substractWidth, y, os));
								
								ns = impNew.getStackIndex(c+1, 2, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x, y + substractHeight, os));
								
								ns = impNew.getStackIndex(c+1, 3, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x + substractWidth, y+substractHeight, os));
								
								ns = impNew.getStackIndex(c+1, 4, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x, y, os));
							}
						}
					}					
				}			
			}else if(frames == 1 && slices > 1){
				impNew = IJ.createHyperStack("hyperstack image v2", newWidth, newHeight, channels, 4, slices, imp.getBitDepth());
				for(int c = 0; c < channels; c++){
					for(int t = 0; t < slices; t++){
						for(int x = 0; x < newWidth; x++){
							for(int y = 0; y < newHeight; y++){
								int os = imp.getStackIndex(c+1, t+1, 1)-1;
															
								int ns = impNew.getStackIndex(c+1, 1, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x+substractWidth, y, os));
								
								ns = impNew.getStackIndex(c+1, 2, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x, y+substractHeight, os));
								
								ns = impNew.getStackIndex(c+1, 3, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x+substractWidth, y+substractHeight, os));
								
								ns = impNew.getStackIndex(c+1, 4, t+1)-1;
								impNew.getStack().setVoxel(x, y, ns, iS.getVoxel(x, y, os));
							}
						}
					}					
				}
			}else{
				return null;
			}
			imp.close();
			System.gc();
			impNew.setCalibration(imp.getCalibration());
			return impNew;
		}
	
	public static int getSharpestPlanePosition(ImagePlus imp, Roi roi){
		/**
		 * @returns the index of the slice image which has the highest Standard Deviation 0 < z < 1
		 * imp: stack-image where the sharpest image shall be found
		 * roi: the region of interest in which the SD is calculated
		 * */		
		double [] SD = new double [imp.getStackSize()];
		double maximum = 0.0; int maximumPos = -1;
		
		double average = 0.0;
		int counter = 0;
		for(int z = 0; z < imp.getStackSize(); z++){
			average = 0.0;
			counter = 0;
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, z) != 0.0 
							&& roi.contains(x,y)){
						average += imp.getStack().getVoxel(x, y, z);
						counter++;
					}
				}
			}	
			average /= counter;
			
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, z)!=0.0 && roi.contains(x,y)){
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
		
		return maximumPos;
	}
}

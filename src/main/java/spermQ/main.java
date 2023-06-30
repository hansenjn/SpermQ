/***===============================================================================
 
 SpermQ_.java Version v0.2.4

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 See the GNU General Public License for more details.
 
 Copyright (C) 2016 - 2023: Jan N Hansen and Jan F Jikeli
   
 For any questions please feel free to contact me (jan.hansen@uni-bonn.de).

==============================================================================**/
package spermQ;

import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

import ij.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.frame.RoiManager;
import ij.text.TextPanel;
import spermQ.jnh.support.*;

public class main implements PlugIn, Measurements{
	//Name
		static final String PLUGINNAME = "SpermQ_";
		static final String PLUGINVERSION = "v0.2.4";
		static final double threshold = 0.70;
		
	//default settings loader
		String [] selectionsDSL = {"Mouse 20x", "Mouse 20x (editable)", 
				"Mouse 32x", "Mouse 32x (editable)",
				"Human 20x", "Human 20x (editable)",
				"Human 32x", "Human 32x (editable)",
				"Tethered Human 32x Fluorescence Measurements", "Tethered Human 32x Fluorescence Measurements (editable)",
				"Mouse 16x", "Mouse 16x (editable)",
				"Mouse 10x", "Mouse 10x (editable)",
				"Norwegian Cilia", "Norwegian Cilia (editable)"}; 
		String selectedDSL = selectionsDSL[0];
		
	//variables
		double xyCal = 0.34375;	//32x
//		double xyCal = 0.55;	//20x (1.6x compared to xyCal 32x)
		double sampleRate = 200;	//mouse; human: 500
		
		String [] thresholdMethods = {"Default", "IJ_IsoData", "Huang", "Intermodes", "IsoData", "Li", "MaxEntropy", "Mean", "MinError", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
//		String selectedThresholdMethod = thresholdMethods [15];	//Triangle (human)
		String selectedThresholdMethod = thresholdMethods [5];	//Li (mouse)
		boolean unifyStartPoints = false, tethered = false;
		boolean repeatGauss = true, blurSelectionOnly = false;
		int upscaleFold = 3;
		double gaussSigma = 2.0;
		boolean addCOM = false;
		
		boolean filterByWidthFit = true;
		int maxVectorLength = 20;
		double normalLength = 5.0;	// 24 for human sperm, 36 for width calibration of human sperm (20x)
		boolean smoothNormal = true,
				saveVNRois = false,
				preventHeadFromCorr = true;
		int preventPoints = 15;
		
		boolean normalizeArcLengths = false;
		
		int plusMinusDistanceSmooth = 5*upscaleFold;
		double maxRefDist = 15;	//mouse; human: 6.4
		double acceptedZIpDistance = 9.6;
		static final String [] zSmoothMethods = {"none", "mean", "median"}; 
		String zSmoothMethod = zSmoothMethods [2];
		
		double curvRefDist = 10.0;
		int groupedTimesteps = 200;
		double neglectedInitialArclength = 20.0;
		
		int hrPlusMinusRange = 10;
	//dialog	
		boolean done = false;		
		ProgressDialog progress;
	
	//calibration algorithm dialog
		int minSharpest = 300, maxSharpest = 400;
		double zStepSize = 0.1;
		
	@Override
	public void run(String arg) {
		// Initialize home path
		String homePath = FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath();
		if(System.getProperty("os.name").toUpperCase().contains("MAC")){
			homePath += System.getProperty("file.separator") + "Desktop";
		}
		
		/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		Load Default Settings
		&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
		
		GenericDialog gdDSL = new GenericDialog("SpermQ Default Settings Loader");		
		gdDSL.setInsets(0,0,0);	gdDSL.addMessage(PLUGINNAME + ", version " + PLUGINVERSION + " (\u00a9 2013-" + constants.dateY.format(new Date()) + ", JN Hansen \u0026 JF Jikeli)", constants.Head1);
		gdDSL.setInsets(10,0,0);	gdDSL.addMessage("Default Settings Loader ", constants.Head2);
		gdDSL.setInsets(10,0,0);	gdDSL.addMessage("Experimental setup ", constants.BoldTxt);
		gdDSL.setInsets(0,0,0);	gdDSL.addNumericField("Sample rate [Hz]", sampleRate, 2);
		gdDSL.setInsets(0,0,0);	gdDSL.addCheckbox("Sperm head is tethered", tethered);
		
		gdDSL.setInsets(10,0,0);	gdDSL.addMessage("Experimental setup ", constants.BoldTxt);
		gdDSL.setInsets(0,0,0);	gdDSL.addChoice("Start with default settings for ", selectionsDSL, selectedDSL);
		gdDSL.setInsets(0,0,0);	gdDSL.addMessage("If you select <editable> settings, you can adjust parameters in the next step.", constants.PlTxt);
		
		gdDSL.setInsets(20,0,0);	gdDSL.addMessage("IMPORTANT NOTE: If you analyze stacks containing lots of frames (e.g. > 4000), the software", constants.PlTxt);
		gdDSL.setInsets(0,0,0);	gdDSL.addMessage("might crush. Thus, it is recommended to split the stack into seperate sub-stacks and analyze those", constants.PlTxt);
		gdDSL.setInsets(0,0,0);	gdDSL.addMessage("sub-stacks separately. Per time-step approximately 2.5MB of RAM are needed.", constants.PlTxt);
		
		gdDSL.showDialog();
		
		sampleRate = gdDSL.getNextNumber();
		tethered = gdDSL.getNextBoolean();
	 	selectedDSL = gdDSL.getNextChoice();
		
		if (gdDSL.wasCanceled())return;
		
		groupedTimesteps = (int)sampleRate;
		double speciesLength = tools2D.SPECIESLENGTH_MOUSE;
		if(selectedDSL.equals(selectionsDSL[0]) || selectedDSL.equals(selectionsDSL[1])){
			//mouse 20x
			xyCal = 0.55;
			unifyStartPoints = false;
			selectedThresholdMethod = thresholdMethods [5];	//Li (mouse)
			upscaleFold = 3;
			addCOM = false;
			maxVectorLength = 20;
			normalLength = 5.0;
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = true;
			preventPoints = 10;
			normalizeArcLengths = false;
			plusMinusDistanceSmooth = 5*upscaleFold;
			maxRefDist = 15;
			acceptedZIpDistance = 9.6;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 10.0;
			neglectedInitialArclength = 20.0;
			hrPlusMinusRange = 10;
			gaussSigma = 2.0;
			filterByWidthFit = true;
			if(tethered){
				filterByWidthFit = false;
				blurSelectionOnly = true;
				repeatGauss = false;
				gaussSigma = 3.0;
			}
			speciesLength = tools2D.SPECIESLENGTH_MOUSE;
		}
		else if(selectedDSL.equals(selectionsDSL[2]) || selectedDSL.equals(selectionsDSL[3])){
			//mouse 32x
			xyCal = 0.34375;
			unifyStartPoints = false;
			selectedThresholdMethod = thresholdMethods [5];	//Li (mouse)
			upscaleFold = 3;
			addCOM = false;
			maxVectorLength = 30;
			normalLength = 5.0;
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = true;
			preventPoints = 15;
			normalizeArcLengths = false;
			plusMinusDistanceSmooth = 7*upscaleFold;
			maxRefDist = 15;
			acceptedZIpDistance = 9.6;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 10.0;
			neglectedInitialArclength = 20.0;
			hrPlusMinusRange = 15;
			gaussSigma = 2.0;
			filterByWidthFit = true;
			if(tethered){
				filterByWidthFit = false;
				blurSelectionOnly = true;
				repeatGauss = false;
				gaussSigma = 3.0;
			}
			speciesLength = tools2D.SPECIESLENGTH_MOUSE;
		}
		else if(selectedDSL.equals(selectionsDSL[4]) || selectedDSL.equals(selectionsDSL[5])){
			//human 20x
			xyCal = 0.55;	//20x
			unifyStartPoints = false;
			selectedThresholdMethod = thresholdMethods [15];	//Triangle (human)
			upscaleFold = 3;
			addCOM = false;
			maxVectorLength = 20;
			normalLength = 6.0;	// 36 for width calibration of human sperm (20x)
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = false;
			preventPoints = 15;
			normalizeArcLengths = false;			
			plusMinusDistanceSmooth = 5*upscaleFold;
			maxRefDist = 6.4;
			acceptedZIpDistance = 9.6;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 10.0;
			neglectedInitialArclength = 0.0;
			hrPlusMinusRange = 10;
			gaussSigma = 0.5;
			filterByWidthFit = false;
			speciesLength = tools2D.SPECIESLENGTH_HUMAN;
		}
		else if(selectedDSL.equals(selectionsDSL[6]) || selectedDSL.equals(selectionsDSL[7])){
			//human 32x
			xyCal = 0.34375;	//32x
			unifyStartPoints = false;
			selectedThresholdMethod = thresholdMethods [15];	//Triangle (human)
			upscaleFold = 3;
			addCOM = false;
			maxVectorLength = 30;
			normalLength = 6.0;	// 36 for width calibration of human sperm (20x)
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = false;
			preventPoints = 15;
			normalizeArcLengths = false;			
			plusMinusDistanceSmooth = 5*upscaleFold;
			maxRefDist = 6.4;
			acceptedZIpDistance = 9.6;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 10.0;
			neglectedInitialArclength = 0.0;
			hrPlusMinusRange = 15;
			gaussSigma = 0.5;
			filterByWidthFit = false;
			speciesLength = tools2D.SPECIESLENGTH_HUMAN;
		}
		else if(selectedDSL.equals(selectionsDSL[8]) || selectedDSL.equals(selectionsDSL[9])){
			//human 32x calcium
			xyCal = 0.34375;	//32x
			unifyStartPoints = true;
			selectedThresholdMethod = thresholdMethods [15];	//Triangle (human)
			upscaleFold = 3;
			addCOM = true;
			maxVectorLength = 20;
			normalLength = 24.0;	// 36 for width calibration of human sperm (20x)
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = false;
			preventPoints = 15;
			normalizeArcLengths = false;			
			plusMinusDistanceSmooth = 5*upscaleFold;
			maxRefDist = 6.4;
			acceptedZIpDistance = 9.6;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 10.0;
			neglectedInitialArclength = 0.0;
			hrPlusMinusRange = 15;
			gaussSigma = 3.0;
			filterByWidthFit = false;
			speciesLength = tools2D.SPECIESLENGTH_HUMAN;
		}
		else if(selectedDSL.equals(selectionsDSL[10]) || selectedDSL.equals(selectionsDSL[11])){
			//mouse 16x
			xyCal = 0.6875;
			unifyStartPoints = false;
			selectedThresholdMethod = thresholdMethods [15];	//Li (mouse) v0.1.8 -> Triangle
			upscaleFold = 3;
			addCOM = false;
			maxVectorLength = 14;
			normalLength = 5.0;
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = true;
			preventPoints = 10;
			normalizeArcLengths = false;
			plusMinusDistanceSmooth = 5*upscaleFold;
			maxRefDist = 10;
			acceptedZIpDistance = 9.6;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 10.0;
			neglectedInitialArclength = 0.0;
			hrPlusMinusRange = 10;
			gaussSigma = 0.5;
			filterByWidthFit = true;
			if(tethered){
				filterByWidthFit = false;
				blurSelectionOnly = false;
				gaussSigma = 1;
			}
			speciesLength = tools2D.SPECIESLENGTH_MOUSE;
		}
		else if(selectedDSL.equals(selectionsDSL[12]) || selectedDSL.equals(selectionsDSL[13])){
			//mouse 10x
			xyCal = 1.1;
			unifyStartPoints = false;
			selectedThresholdMethod = "Triangle";	//Li (mouse)
			upscaleFold = 3;
			addCOM = false;
			maxVectorLength = 20;
			normalLength = 5.0;
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = true;
			preventPoints = 10;
			normalizeArcLengths = false;
			plusMinusDistanceSmooth = 3*upscaleFold;
			maxRefDist = 10;
			acceptedZIpDistance = 9.6;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 10.0;
			neglectedInitialArclength = 10.0;
			hrPlusMinusRange = 10;
			gaussSigma = 0.5;
			filterByWidthFit = true;
			if(tethered){
				filterByWidthFit = false;
				blurSelectionOnly = true;
				repeatGauss = false;
				gaussSigma = 0.5;
			}
			speciesLength = tools2D.SPECIESLENGTH_MOUSE;
		}
		else if(selectedDSL.equals(selectionsDSL[14]) || selectedDSL.equals(selectionsDSL[15])){
			//Norwegian Cilia
			xyCal = 0.325;
			unifyStartPoints = true;
			selectedThresholdMethod = "Li";
			upscaleFold = 3;
			addCOM = false;
			maxVectorLength = 20;
			normalLength = 2;
			smoothNormal = true;
			saveVNRois = false;
			preventHeadFromCorr = true;
			preventPoints = 10;
			normalizeArcLengths = false;
			plusMinusDistanceSmooth = 1*upscaleFold;
			maxRefDist = 1.5;
			acceptedZIpDistance = 1.5;
			zSmoothMethod = zSmoothMethods [2];
			curvRefDist = 3.0;
			neglectedInitialArclength = 0.0;
			hrPlusMinusRange = 1;
			gaussSigma = 1.0;
			filterByWidthFit = false;
			repeatGauss = false;
			blurSelectionOnly = false;
			groupedTimesteps = 1024;
			speciesLength = tools2D.CILIALENGTH_BRAIN_ZEBRAFISH;
		}
		
		/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		GenericDialog
		&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
		
		if(selectedDSL.equals(selectionsDSL[1]) || selectedDSL.equals(selectionsDSL[3]) || selectedDSL.equals(selectionsDSL[5])
				 || selectedDSL.equals(selectionsDSL[7]) || selectedDSL.equals(selectionsDSL[9]) || selectedDSL.equals(selectionsDSL[11])
				 || selectedDSL.equals(selectionsDSL[13]) || selectedDSL.equals(selectionsDSL[15])){
			GenericDialog gd = new GenericDialog(PLUGINNAME);		
//			setInsets(top, left, bottom)
			gd.setInsets(0,0,0);	gd.addMessage(PLUGINNAME + ", version " + PLUGINVERSION + " (\u00a9 2013-" + constants.dateY.format(new Date()) + ", JF Jikeli \u0026 JN Hansen)", constants.Head1);
					
			gd.setInsets(10,0,0);	gd.addNumericField("xy calibration [um]", xyCal, 5);
							
			gd.setInsets(10,0,0);	gd.addMessage("Trace generation ", constants.BoldTxt);
			gd.setInsets(10,0,0);	gd.addChoice("Thresholding Method", thresholdMethods, selectedThresholdMethod);
			gd.setInsets(0,0,0);	gd.addNumericField("Gauss sigma (defines size of detected objects)", gaussSigma, 2);
			gd.setInsets(0,0,0);	gd.addCheckbox("Repeat gauss fit after binarization", repeatGauss);
			gd.setInsets(0,0,0);	gd.addCheckbox("Blur only inside ROI selection (recommended for tethered mouse sperm)", blurSelectionOnly);
			gd.setInsets(0,0,0);	gd.addNumericField("Upscaling of points (fold)", upscaleFold, 0);
			gd.setInsets(0,0,0);	gd.addCheckbox("Add head center-of-mass as first point", addCOM);
			gd.setInsets(0,0,0);	gd.addCheckbox("Unify start points (for tethered sperm only)", unifyStartPoints);
			
			gd.setInsets(10,0,0);	gd.addMessage("XY precision and gauss fits", constants.BoldTxt);
			gd.setInsets(0,0,0);	gd.addCheckbox("Filter points by gauss fits (remove points with unacceptable fit results)", filterByWidthFit);
			gd.setInsets(0,0,0);	gd.addNumericField("Maximum vector length (points)", maxVectorLength, 0);
			gd.setInsets(0,0,0);	gd.addNumericField("Normal radius for gauss fit [um]", normalLength, 2);
			gd.setInsets(0,0,0);	gd.addCheckbox("Exclude head from correction / deletion (initial (" + preventPoints + " * upscaling factor) points)", preventHeadFromCorr);
			gd.setInsets(0,0,0);	gd.addCheckbox("Smooth normal for XY gauss fit", smoothNormal);
			gd.setInsets(0,0,0);	gd.addCheckbox("Save Roi-sets of vectors and normals", saveVNRois);
			
			gd.setInsets(10,0,0);	gd.addMessage("Smoothing", constants.BoldTxt);
			gd.setInsets(0,0,0);	gd.addChoice("Z (= fit width) smoothing method", zSmoothMethods, zSmoothMethod);
			gd.setInsets(0,0,0);	gd.addNumericField("Accepted xy distance of points for fit-width-smoothing [um]", acceptedZIpDistance, 5);
			gd.setInsets(0,0,0);	gd.addNumericField("# (+/-)-consecutive points for xy- and fit-width-smoothing", plusMinusDistanceSmooth, 0);
			gd.setInsets(0,0,0);	gd.addNumericField("Distance of point to first point to form the reference=orientation vector [um]", maxRefDist, 5);
						
			gd.setInsets(10,0,0);	gd.addMessage("Additional calculations", constants.BoldTxt);
			gd.setInsets(0,0,0);	gd.addNumericField("Curvature: reference point distance", curvRefDist, 4);
			gd.setInsets(0,0,0);	gd.addNumericField("FFT: Grouped consecutive time-steps", groupedTimesteps, 0);
			gd.setInsets(0,0,0);	gd.addNumericField("FFT: Do not analyze initial ... µm from head", neglectedInitialArclength, 0);
			gd.setInsets(0,0,0);	gd.addNumericField("Head rotation matrix radius", hrPlusMinusRange, 0);

			gd.setInsets(10,0,0);	gd.addMessage("Output of summary file", constants.BoldTxt);
			gd.setInsets(-3,0,0);		gd.addMessage("In the field below you may, if needed, adapt the file path where the file summarizing results for all analyzed images will be stored.", constants.PlTxt);
			gd.setInsets(-3,0,0);		gd.addStringField("File path for output of the summary txt-file: ", homePath, 30);
			
			
			gd.showDialog();
		
		 	xyCal = gd.getNextNumber();
		 		  	
		 	selectedThresholdMethod = gd.getNextChoice();
		 	gaussSigma = (double) gd.getNextNumber();
		 	repeatGauss = gd.getNextBoolean();
		 	blurSelectionOnly = gd.getNextBoolean();
			upscaleFold = (int) gd.getNextNumber();
			addCOM = gd.getNextBoolean();
			unifyStartPoints = gd.getNextBoolean();
			
			filterByWidthFit = gd.getNextBoolean();
			maxVectorLength = (int) gd.getNextNumber();
			normalLength = (double) gd.getNextNumber();
			preventHeadFromCorr = gd.getNextBoolean();
			smoothNormal = gd.getNextBoolean();
			saveVNRois = gd.getNextBoolean();

			zSmoothMethod = gd.getNextChoice();
			acceptedZIpDistance = (double) gd.getNextNumber();
			plusMinusDistanceSmooth = (int) gd.getNextNumber();
			maxRefDist = (double) gd.getNextNumber();
			
			curvRefDist = (double) gd.getNextNumber();
			groupedTimesteps = (int) gd.getNextNumber();
			neglectedInitialArclength = (double) gd.getNextNumber();			
			hrPlusMinusRange = (int) hrPlusMinusRange;
		 	homePath = gd.getNextString();
			
			if (gd.wasCanceled())return;
		}
			
		preventPoints *= upscaleFold;
		if(preventHeadFromCorr == false){
			preventPoints = 0;
		}
		
		int encoding = 0;
  		if(zSmoothMethod.equals(zSmoothMethods[0])){
  			encoding = tools2D.PUREZ;					  			  		
  		}else if(zSmoothMethod.equals(zSmoothMethods[1])){
	  		encoding = tools2D.MEANZ;	
  		}else if(zSmoothMethod.equals(zSmoothMethods[2])){
	  		encoding = tools2D.MEDIANZ;	
	  	}
		
		/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		Initiate multi task management
		&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
		try{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}catch(Exception e){}
		
		//image selection
		OpenFilesDialog od = new OpenFilesDialog ();
		od.setLocation(0,0);
		od.setVisible(true);
		
		od.addWindowListener(new java.awt.event.WindowAdapter() {
	        public void windowClosing(WindowEvent winEvt) {
//	        	IJ.log("Analysis canceled!");
	        	return;
	        }
	    });

		//Waiting for od to be done
		while(od.done==false){
			 try{
				 Thread.currentThread().sleep(50);
		     }catch(Exception e){
		     }
		}
		
		int tasks = od.filesToOpen.size();
		String [] name = new String [tasks];
		String [] dir = new String [tasks];
		boolean tasksSuccessfull [] = new boolean [tasks];
		for(int task = 0; task < tasks; task++){
			name [task] = od.filesToOpen.get(task).getName();
			dir [task] = od.filesToOpen.get(task).getParent() + System.getProperty("file.separator");
			tasksSuccessfull [task] = false;
		}	
		
		
		//start progress dialog
		progress = new ProgressDialog(name, tasks);
		progress.setVisible(true);
		progress.addWindowListener(new java.awt.event.WindowAdapter() {
	        public void windowClosing(WindowEvent winEvt) {
	        	progress.stopProcessing();
	        	if(done==false){
	        		IJ.error("Script stopped...");
	        	}       	
	        	System.gc();
	        	return;
	        }
		});	
		
		/**&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		Processing
		&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&*/
		
		//Initialize
		ImagePlus imp;
		
		//Checking for existing saving path for the summary
		if(!new File(homePath).exists()) {
			while(true) {
				GenericDialog gd = new GenericDialog(PLUGINNAME);		
//				setInsets(top, left, bottom)
				gd.setInsets(0,0,0);	gd.addMessage(PLUGINNAME + ", version " + PLUGINVERSION + " (\u00a9 2013-" + constants.dateY.format(new Date()) + ", JF Jikeli \u0026 JN Hansen)", constants.Head1);
						
				gd.setInsets(10,0,0);	gd.addMessage("The path for saving the summary file does not exist.", constants.PlTxt);
				gd.setInsets(0,0,0);	gd.addMessage("Correct the path in the box below or enter an existing path for storing the summary file.", constants.PlTxt);
				
				gd.setInsets(10,0,0);	gd.addStringField("File path for saving the summary file: ", homePath, 30);
				
				gd.showDialog();
			
			 	homePath = gd.getNextString();
				
				if (gd.wasCanceled()) return;
				
				if(new File(homePath).exists()) {
					break;
				}
			}
		}
		
		
		//get head selections
		Roi [] selections = new Roi [tasks];
		if(tethered){
			IJ.setTool("oval");
		}else{
			IJ.setTool("polygon");
		}
		{
			ImagePlus maxImp;
			for(int task = 0; task < tasks; task++){
				imp = IJ.openVirtual(dir [task] + name [task]);
				IJ.run(imp, "Z Project...", "projection=[Max Intensity]");
				maxImp = WindowManager.getCurrentImage();
				
				imp.changes = false;
				imp.close();
								
				while(true){
					progress.replaceBarText("user interaction required... [task " + (task+1) + "/" + tasks + "]");
					new WaitForUserDialog("Set a Roi containing parts of the cell in every frame [task " + (task+1) + "/" + tasks + "]").show();
					if(maxImp.getRoi()!=null) break;
				}		
				selections [task] = new PolygonRoi(maxImp.getRoi().getPolygon(), PolygonRoi.POLYGON);
				
				maxImp.changes = false;
				maxImp.close();
				System.gc();
			}
		}
		System.gc();
		
		//initialize
		double [][] freqSummaryTheta2D = new double [tasks][3];
    	double [][] freqSummaryHrMaxPos = new double [tasks][3];
    	double [][] freqSummaryHrMaxInt = new double [tasks][3];
    	double [][] freqSummaryHrAngle = new double [tasks][3];
    	double [][][] freqSummaryX = new double [tasks][3][3];
		double [][][] freqSummaryY = new double [tasks][3][3];
		double [][][] freqSummaryZ = new double [tasks][3][3];
		double [][][] freqSummaryCurv = new double [tasks][3][3];
		double [][][] freqSummaryCAngle = new double [tasks][3][3];
		
		ArrayList <trace2D> traces = null;
		RoiManager rm;
		double medianArcLength = 0.0;			
		double minIntensity = Double.POSITIVE_INFINITY,
		  		maxIntensity = 0.0,
		  		minPosition = Double.POSITIVE_INFINITY,
		  		maxPosition = 0.0;
		Date startDate;
		TextPanel tp;
		
		//processing
		int groupedTimestepsForSeq;
		tasking: for(int task = 0; task < tasks; task++){
			progress.updateBarText("in progress...");
			startDate = new Date();
			progress.clearLog();
			try{
				running: while(true){
					// get image and open
				    	String saveName = name [task].substring(0,name [task].lastIndexOf(".")) + "_spq_" + constants.dateName.format(startDate);
				    	new File(dir [task]+ saveName).mkdirs();
				    	imp = IJ.openImage(dir [task] + name [task]);	
				    	imp.getCalibration().pixelHeight = xyCal;
				    	imp.getCalibration().pixelWidth = xyCal;
				    					    	
				    	String savePath = dir [task]+ saveName + System.getProperty("file.separator") + saveName;			    	
				  	// get image and open (end)				
					
					//Check if image ist processable
						if(imp.getBitDepth()==24){
				    		progress.notifyMessage("ERROR: Cannot process color images!", ProgressDialog.ERROR);
					  		break running;
				    	}
					
					//Check stack properties
						if(imp.getNSlices() > 1 && imp.getNFrames() == 1){
							HyperStackConverter.toHyperStack(imp, 1, 1, imp.getNSlices());
							progress.notifyMessage("Slices and frames swapped for processing.",ProgressDialog.LOG);						
						}else if(imp.getNSlices() > 1){
							progress.notifyMessage("ERROR: No processing of hyper-stack images possible!", ProgressDialog.ERROR);
							break running;
						}else if(imp.getNChannels() > 1){
							progress.notifyMessage("ERROR: No processing of multi-channel images possible!", ProgressDialog.ERROR);
							break running;
						}
					
						groupedTimestepsForSeq = groupedTimesteps;
				    	if(imp.getNFrames() < groupedTimestepsForSeq){
				    		groupedTimestepsForSeq = imp.getNFrames();
				    		progress.notifyMessage("Nr of grouped Timesteps for FFT was reduced to " + groupedTimestepsForSeq, ProgressDialog.LOG);
				    	}
						
					 //get point list
					  	progress.updateBarText("get object traces...");			
					  	
					  	//TODO: first normalize intensities in the image to reduce difference between e.g. head and flagellar tip
					  	//TODO: second let the user set a user-based threshold
					  	
					  	traces = tools2D.getObjectTraces(imp, selectedThresholdMethod, gaussSigma, progress, selections [task], maxRefDist, repeatGauss, blurSelectionOnly);			  	
					  	if(progress.isStopped())break running;
					  						  	
					//if tethered average starting point
					  	if(tethered || unifyStartPoints){
					  		tools2D.reverseReversedTraces(traces, progress);	//METHOD IMPROVED: 15.01.2019 -> use distance instead of absolute x + y difference
					  		if(unifyStartPoints){
						  		tools2D.unifyStartPoints(traces, progress);
						  		if(progress.isStopped())break running;
						  	}
					  	}else{
					  		//Mode for free-swimming sperm - reverse all into one direction and than check which side is the one that moves forward
					  		tools2D.reverseReversedTracesOfFree(traces, progress, 30);	//METHOD introduced 14.08.2019
					  	}
					  	
					  	/**
					  	 * compare directions in steps of fps and orient traces accordingly				  	 * 
					  	 * */
					  	
					//function to add head center of mass
					  	if(addCOM){
					  		tools2D.add1stCOM(traces, imp, progress);
					  	}
					  	
					//upscale point trace
					  	progress.updateBarText("upscale traces...");
					  	for(int i = 0; i < traces.size(); i++){
					  		traces.get(i).upscalePoints(upscaleFold);
					  	}
					  	
//					  	tools2D.saveTraceImage(imp, traces, tools2D.NOZ, savePath + "_up");	//TODO remove
//					  	System.gc();
										  	
					//improve x,y and calculate xy-gaussWidth
				  		progress.updateBarText("improve traces...");
				  		tools2D.adjustPointsViaNormalVector(imp, traces, progress, saveVNRois, dir [task] + saveName + System.getProperty("file.separator"), 
				  				maxVectorLength, normalLength, smoothNormal, preventHeadFromCorr, preventPoints, filterByWidthFit);	
				  		if(progress.isStopped())break running;
					  	
//					  	tools2D.saveTraceImage(imp, traces, tools2D.NOZ, savePath + "_pCorr");
//					  	System.gc();
					
					//linear interpolate xy
				  		for(int i = 0; i < traces.size(); i++){
				  			progress.updateBarText("resorting points ... (" + (i+1) + "/" + traces.size() + ")");
				  			traces.get(i).sortPointsAndRemoveOutliers(progress, tools2D.NOZ);			  			
				  		}
//					  	tools2D.saveTraceImage(imp, traces, tools2D.NOZ, savePath + "pfiS1");
				  		
				  	 	
				  	 	for(int i = 0; i < traces.size(); i++){
				  	 		progress.updateBarText("interpolate xy ... (" + (i+1) + "/" + traces.size() + ")");
				  	 		traces.get(i).interpolateXYLinear(plusMinusDistanceSmooth, tools2D.MEAN);
				  		}
			  	 		
//				  	 	tools2D.saveTraceImage(imp, traces, tools2D.NOZ, savePath + "pIp");
			  	 		
					  	for(int i = 0; i < traces.size(); i++){
					  		progress.updateBarText("resorting points ... (" + (i+1) + "/" + traces.size() + ")");
				  			traces.get(i).sortPointsAndRemoveOutliers(progress ,tools2D.NOZ);
				  			
				  		}
				  		tools2D.saveTraceImage(imp, traces, tools2D.NOZ, savePath + "");		  		
				  		System.gc();
					 
					  
					//obtain better width fit data (no xy correction)
						progress.updateBarText("get width fit data...");
						tools2D.updateWidthFitAndGetRelativeZ(imp, traces, progress,  saveVNRois, dir [task] + saveName + System.getProperty("file.separator"),
								maxVectorLength, normalLength, smoothNormal, preventHeadFromCorr, preventPoints);
						if(progress.isStopped())break running;
						
						System.gc();
				  				
					//calculate arc lengths		
						if(normalizeArcLengths){
							progress.updateBarText("find average maximum arcLength..");
							double median [] = new double [traces.size()];
							for(int i = 0; i < traces.size(); i++){
								median [i] = traces.get(i).getArcLengthXY();
							}
							medianArcLength = tools.getMedian(median);									  	
						}
						if(progress.isStopped())break running;
						System.gc();
						
						progress.updateBarText("calculate arc lengths...");
					  	for(int i = 0; i < traces.size(); i++){
					  		progress.updateBarText("calculate arc lengths... (" + (i+1) + "/" + traces.size() + ")");
					  		traces.get(i).calculateArcLengthPositions(normalizeArcLengths, medianArcLength);
					  	}						
						if(progress.isStopped())break running;  	
					  	
					//save raw width results and trace
//					  	tools2D.saveXYWidthGraph(traces, xyCal, dir [task] + saveName + System.getProperty("file.separator") + saveName);
//					  	tools.saveTraceImage(imp, traces, tools.NOZ, savePath);
//					  	System.gc();
					  							  	
					  	//interpolate z fit	//TODO only median
					  		progress.updateBarText("interpolate z...");
					  		tools2D.interpolateZLinear(traces, progress, acceptedZIpDistance, plusMinusDistanceSmooth);
//					  		for(int i = 0; i < traces.size(); i++){
//					  			traces.get(i).sortPointsAndRemoveOutliers(progress ,tools2D.G4PZMEDIAN);
//					  		}			  		
					  		if(progress.isStopped())break running;
				  		
				  		//determine orientation vector and oriented points				  	
					  		for(int i = 0; i < traces.size(); i++){
								progress.updateBarText("determining arc length and orientation vector... (" + (i+1) + "/" + traces.size() + ")");
//								traces.get(i).calculateArcLengthPositions(normalizeArcLengths, medianArcLength);
								traces.get(i).updateAllOrientationVectors(maxRefDist, progress);					
								traces.get(i).calculateOrientedPointsAll2D();
							}
							if(progress.isStopped())break running;
							
						//filter traces
							tools2D.removeProblematicTraces(progress, traces);					
							if(progress.isStopped())break running;
							
						//determine curvature and angles			  	
					  		for(int i = 0; i < traces.size(); i++){
								progress.updateBarText("determining arc length and orientation vector... (" + (i+1) + "/" + traces.size() + ")");
								traces.get(i).calculateDAnglesAndCurvatures(curvRefDist, preventPoints);
							}
					  		if(progress.isStopped())break running;
					  		System.gc();					
							
					  //calculate head rotation parameters and save data
					  	minIntensity = Double.POSITIVE_INFINITY;
					 	maxIntensity = 0.0;
					  	minPosition = Double.POSITIVE_INFINITY;
					  	maxPosition = 0.0;
					  						
				  		for(int i = 0; i < traces.size(); i++){
				  			progress.updateBarText("calculating head rotation paramters... (" + (i+1) + "/" + traces.size() + ")");
				  			try{			  				
					  			if(!traces.get(i).oriented)	continue;
					  			traces.get(i).calculateHeadRotationAngle2D(imp, hrPlusMinusRange, progress);
					  			if(!traces.get(i).hrVset)	continue;
					  			
				  				if(traces.get(i).getHeadRotationMaximumPositions() > maxPosition){
					  				maxPosition = traces.get(i).getHeadRotationMaximumPositions();  
					  			}
				  				if(traces.get(i).getHeadRotationMaximumPositions() < minPosition){
					  				minPosition = traces.get(i).getHeadRotationMaximumPositions();  
					  			}
				  				if(traces.get(i).getHeadRotationMaximumIntensities() > maxIntensity){
					  				maxIntensity = traces.get(i).getHeadRotationMaximumIntensities();  
					  			}
				  				if(traces.get(i).getHeadRotationMaximumIntensities() < minIntensity){
					  				minIntensity = traces.get(i).getHeadRotationMaximumIntensities();  
					  			}
				  			}catch(Exception e){
				  				String out = "";
								for(int err = 0; err < e.getStackTrace().length; err++){
									out += " \n " + e.getStackTrace()[err].toString();
								}
				  				progress.notifyMessage("Calculating head rotation failed for t=" + traces.get(i).getFrame() + " - error message: " + out, ProgressDialog.LOG);
				  				traces.remove(i);
				  				traces.trimToSize();
				  			}
				  			
				  		}
				  		if(progress.isStopped())break running;
				  		
				  		progress.updateBarText("saving head rotation matrix...");
				  		tools2D.saveHeadRotationMatrixImage(traces, imp.getCalibration().pixelWidth, savePath);
				  		if(progress.isStopped())break running;
					  	System.gc();
					  	
					  	progress.updateBarText("calculate X frequencies");
					  	freqSummaryX [task] = tools2D.getAndSaveFrequencies(traces, savePath, xyCal, 
					  			groupedTimestepsForSeq, tools2D.KYMOX, tools2D.NOZ, sampleRate, neglectedInitialArclength, speciesLength);
					  	progress.updateBarText("calculate Y frequencies");
					  	freqSummaryY [task] = tools2D.getAndSaveFrequencies(traces, savePath, xyCal, 
					  			groupedTimestepsForSeq, tools2D.KYMOY, tools2D.NOZ, sampleRate, neglectedInitialArclength, speciesLength);
					  	progress.updateBarText("calculate Z frequencies ...");
				  		freqSummaryZ [task] = tools2D.getAndSaveFrequencies(traces, savePath, xyCal, 
				  				groupedTimestepsForSeq, tools2D.KYMOZ, encoding, sampleRate, neglectedInitialArclength, speciesLength);
				  		System.gc();
				  		
				  		progress.updateBarText("calculate curvature frequencies ...");
				  		freqSummaryCurv [task] = tools2D.getAndSaveFrequencies(traces, savePath, xyCal, 
				  				groupedTimestepsForSeq, tools2D.KYMOCURV, tools2D.NOZ, sampleRate, neglectedInitialArclength, speciesLength);
				  		progress.updateBarText("calculate curvature angle frequencies ...");
				  		freqSummaryCAngle [task] = tools2D.getAndSaveFrequencies(traces, savePath, xyCal, groupedTimestepsForSeq,
				  				tools2D.KYMOCANGLEXY, tools2D.NOZ, sampleRate, neglectedInitialArclength, speciesLength);
						System.gc();
				  		
				  		progress.updateBarText("calculate theta freqs ...");
				  		freqSummaryTheta2D [task] = tools2D.getAndSaveGroupedFrequenciesTraceParam(traces,
				  				tools2D.TRACE_THETA, groupedTimestepsForSeq, sampleRate, savePath, 
				  				minIntensity, maxIntensity, minPosition, maxPosition);
				  		progress.updateBarText("calculate head rotation freqs ...");
				  		freqSummaryHrMaxPos [task] = tools2D.getAndSaveGroupedFrequenciesTraceParam(traces,
				  				tools2D.TRACE_HRMAXPOSITION, groupedTimestepsForSeq, sampleRate, savePath, 
				  				minIntensity, maxIntensity, minPosition, maxPosition);
				  		freqSummaryHrMaxInt [task] = tools2D.getAndSaveGroupedFrequenciesTraceParam(traces,
				  				tools2D.TRACE_HRMAXINTENSITY, groupedTimestepsForSeq, sampleRate, savePath, 
				  				minIntensity, maxIntensity, minPosition, maxPosition);
				  		freqSummaryHrAngle [task] = tools2D.getAndSaveGroupedFrequenciesTraceParam(traces,
				  				tools2D.TRACE_HRANGLE, groupedTimestepsForSeq, sampleRate, savePath, 
				  				minIntensity, maxIntensity, minPosition, maxPosition);					  		
						System.gc();
					  					  	
					//save into file
					  	Date saveDate = new Date();
					  	tp = new TextPanel("Results");
					  	tp.append("Saving date:	" + constants.dateTab.format(saveDate) + "	Analysis started:	" + constants.dateTab.format(startDate));
						tp.append("Processed image:	" + name [task]);
						tp.append("");
						tp.append("SETTINGS: ");
						tp.append("	" + "Sample rate [Hz]:	" + constants.df6US.format(sampleRate));
						tp.append("	" + "Tethered sperm:	" + tethered);
						tp.append("	" + "xy calibration [um]:	" + constants.df6US.format(xyCal));
						tp.append("	TRACE GENERATION");
						tp.append("	" + "Selected thresholding method:	" + selectedThresholdMethod);
						tp.append("	" + "Gaussian blur for trace generation - sigma:	" + constants.df3US.format(gaussSigma));
						tp.append("	" + "Repeat gaussian blur after binarization:	" + repeatGauss);
						tp.append("	" + "Blur only inside manual selection:	" + blurSelectionOnly);
						tp.append("	" + "Upscale trace (fold):	" + upscaleFold);
						tp.append("	" + "Head center-of-mass added as first point of trace:	" + addCOM);
						tp.append("	" + "Unify start points (e.g. for tethered sperm):	" + unifyStartPoints);
						tp.append("	XY PRECISION AND GAUSS FITS");
						tp.append("	" + "Filter out points with unaccepted normal gauss fit:	" + filterByWidthFit);
						tp.append("	" + "Max vector length [points]:	" + constants.df0.format(maxVectorLength));
						tp.append("	" + "Normal vector radius [um]:	" + constants.df6US.format(normalLength));
						tp.append("	" + "Exclude head points from correction / deletion (initial " + preventPoints + " points):	" + preventHeadFromCorr);
						tp.append("	" + "Smooth normal for xy fit:	" + smoothNormal);
						tp.append("	SMOOTHING");
						tp.append("	" + "Z smoothing method:	" + zSmoothMethod);
						tp.append("	" + "Accepted xy distance for fit-width-smoothing:	" + constants.df6US.format(acceptedZIpDistance));
						tp.append("	" + "# points before or after individual point included in xy- and fit-width-smoothing:	" + constants.df0.format(plusMinusDistanceSmooth));
						tp.append("	" + "Maximum distance of points to find a reference vector:	" + constants.df6US.format(maxRefDist));
						tp.append("	ADDITIONAL CALCULATIONS:");
						tp.append("	" + "Curvature and dAngle Calcultion - distance of upstream reference point:	" + constants.df6US.format(curvRefDist));
						tp.append("	" + "Grouped timesteps for fourier transform:	" + constants.df0.format(groupedTimestepsForSeq));
						tp.append("	" + "Initial arclengths neglected for forier transform (µm):	" + constants.df6US.format(neglectedInitialArclength));
						tp.append("	" + "Head rotation matrix radius (points):	" + constants.df0.format(hrPlusMinusRange));
						tp.append("");
								
						tp.append("RESULTS:");
						tp.append("Angle Theta [°]:		2D");
						for(int i = 0; i < traces.size(); i++){
							tp.append("	" + traces.get(i).getFrame()
									+ "	" + constants.df6US.format(traces.get(i).getThetaDegree(tools2D.NOZ)));
						}
						tp.append("	" + "Average found primary freq.:" 
										+ "	" + constants.df6US.format(freqSummaryTheta2D[task][0]));
						tp.append("	" + "Average found secondary freq.:" 
								+ "	" + constants.df6US.format(freqSummaryTheta2D[task][1]));
						tp.append("	" + "Average COM freq.:" 
								+ "	" + constants.df6US.format(freqSummaryTheta2D[task][2]));
						
						tp.append("");
						tp.append("head position		x [µm]	y [µm]	width (no interpol) [µm]	width (mean-smooth) [µm]	width (median-smooth) [µm]");
						for(int i = 0; i < traces.size(); i++){
							tp.append("	" + traces.get(i).getFrame()
									+ "	" + constants.df6US.format(traces.get(i).getTracePoints().get(0).getX())
									+ "	" + constants.df6US.format(traces.get(i).getTracePoints().get(0).getY())
									+ "	" + constants.df6US.format(traces.get(i).getTracePoints().get(0).getZ(tools2D.PUREZ))
									+ "	" + constants.df6US.format(traces.get(i).getTracePoints().get(0).getZ(tools2D.MEANZ))
									+ "	" + constants.df6US.format(traces.get(i).getTracePoints().get(0).getZ(tools2D.MEDIANZ)));
						}
						
						tp.append("");
						tp.append("head rotation	"
								+ "	" + "Maximum position"
								+ "	" + "Maximum position (normalized)"
								+ "	" + "Maximum intensity "
								+ "	" + "Maximum intensity (normalized)"
								+ "	" + "position-intensity vector angle");
						
						String appendTxt;
						for(int i = 0; i < traces.size(); i++){		
							if(!traces.get(i).hrVset) continue;
							appendTxt = "";
							appendTxt += "	" + constants.df0.format(traces.get(i).getFrame());
							appendTxt += "	" + constants.df6US.format(traces.get(i).getHeadRotationMaximumPositions());											
							appendTxt += "	" + constants.df6US.format(tools.getNormalizedValue(
								traces.get(i).getHeadRotationMaximumPositions(), minPosition, maxPosition) - 0.5);
							appendTxt += "	" + constants.df6US.format(traces.get(i).getHeadRotationMaximumIntensities());
							appendTxt += "	" + constants.df6US.format(tools.getNormalizedValue(
								traces.get(i).getHeadRotationMaximumIntensities(), minIntensity, maxIntensity) - 0.5);
							appendTxt += "	" + constants.df6US.format(traces.get(i).getHeadRotationAngle(minIntensity, maxIntensity, minPosition, maxPosition));
							tp.append(appendTxt);
						}
						tp.append("Average found primary freq.	"
								+ "	" + constants.df6US.format(freqSummaryHrMaxPos [task][0])
								+ "	" + ""
								+ "	" + constants.df6US.format(freqSummaryHrMaxInt [task][0])
								+ "	" + ""
								+ "	" + constants.df6US.format(freqSummaryHrAngle [task][0]));
						tp.append("Average found secondary freq.	"
								+ "	" + constants.df6US.format(freqSummaryHrMaxPos [task][1])
								+ "	" + ""
								+ "	" + constants.df6US.format(freqSummaryHrMaxInt [task][1])
								+ "	" + ""
								+ "	" + constants.df6US.format(freqSummaryHrAngle [task][1]));
						tp.append("COM frequency	"
								+ "	" + constants.df6US.format(freqSummaryHrMaxPos [task][2])
								+ "	" + ""
								+ "	" + constants.df6US.format(freqSummaryHrMaxInt [task][2])
								+ "	" + ""
								+ "	" + constants.df6US.format(freqSummaryHrAngle [task][2]));
						tp.append("");
						
						tp.append("Average found frequencies for arc-length oriented points (X)");
						tp.append("	" + "	" + "1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
						tp.append("	" + "1st frequency peak	" + constants.df6US.format(freqSummaryX [task][0][0]) 
							+ "	" + constants.df6US.format(freqSummaryX [task][0][1])
							+ "	" + constants.df6US.format(freqSummaryX [task][0][2]));
						tp.append("	" + "2nd frequency peak	" + constants.df6US.format(freqSummaryX [task][1][0]) 
							+ "	" + constants.df6US.format(freqSummaryX [task][1][1])
							+ "	" + constants.df6US.format(freqSummaryX [task][1][2]));
						tp.append("	" + "COM frequency	" + constants.df6US.format(freqSummaryX [task][2][0]) 
							+ "	" + constants.df6US.format(freqSummaryX [task][2][1])
							+ "	" + constants.df6US.format(freqSummaryX [task][2][2]));
						tp.append("");
						
						tp.append("Average found frequencies for arc-length oriented points (Y)");
						tp.append("	" + "	" + "1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
						tp.append("	" + "1st frequency peak	" + constants.df6US.format(freqSummaryY [task][0][0]) 
							+ "	" + constants.df6US.format(freqSummaryY [task][0][1])
							+ "	" + constants.df6US.format(freqSummaryY [task][0][2]));
						tp.append("	" + "2nd frequency peak	" + constants.df6US.format(freqSummaryY [task][1][0]) 
							+ "	" + constants.df6US.format(freqSummaryY [task][1][1])
							+ "	" + constants.df6US.format(freqSummaryY [task][1][2]));
						tp.append("	" + "COM frequency	" + constants.df6US.format(freqSummaryY [task][2][0]) 
							+ "	" + constants.df6US.format(freqSummaryY [task][2][1])
							+ "	" + constants.df6US.format(freqSummaryY [task][2][2]));
						tp.append("");
						
						tp.append("Average found frequencies for arc-length oriented points (Z)");
						tp.append("	" + "	" + "1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
						tp.append("	" + "1st frequency peak	" + constants.df6US.format(freqSummaryZ [task][0][0]) 
							+ "	" + constants.df6US.format(freqSummaryZ [task][0][1])
							+ "	" + constants.df6US.format(freqSummaryZ [task][0][2]));
						tp.append("	" + "2nd frequency peak	" + constants.df6US.format(freqSummaryZ [task][1][0]) 
							+ "	" + constants.df6US.format(freqSummaryZ [task][1][1])
							+ "	" + constants.df6US.format(freqSummaryZ [task][1][2]));
						tp.append("	" + "COM frequency	" + constants.df6US.format(freqSummaryZ [task][2][0]) 
							+ "	" + constants.df6US.format(freqSummaryZ [task][2][1])
							+ "	" + constants.df6US.format(freqSummaryZ [task][2][2]));
						tp.append("");
						
						tp.append("Average found frequencies for arc-length oriented points (Curvature)");
						tp.append("	" + "	" + "1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
						tp.append("	" + "1st frequency peak	" + constants.df6US.format(freqSummaryCurv [task][0][0]) 
							+ "	" + constants.df6US.format(freqSummaryCurv [task][0][1])
							+ "	" + constants.df6US.format(freqSummaryCurv [task][0][2]));
						tp.append("	" + "2nd frequency peak	" + constants.df6US.format(freqSummaryCurv [task][1][0]) 
							+ "	" + constants.df6US.format(freqSummaryCurv [task][1][1])
							+ "	" + constants.df6US.format(freqSummaryCurv [task][1][2]));
						tp.append("	" + "COM frequency	" + constants.df6US.format(freqSummaryCurv [task][2][0]) 
							+ "	" + constants.df6US.format(freqSummaryCurv [task][2][1])
							+ "	" + constants.df6US.format(freqSummaryCurv [task][2][2]));
						tp.append("");
						
						tp.append("Average found frequencies for arc-length oriented points (Curvature Angle)");
						tp.append("	" + "	" + "1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
						tp.append("	" + "1st frequency peak	" + constants.df6US.format(freqSummaryCAngle [task][0][0]) 
							+ "	" + constants.df6US.format(freqSummaryCAngle [task][0][1])
							+ "	" + constants.df6US.format(freqSummaryCAngle [task][0][2]));
						tp.append("	" + "2nd frequency peak	" + constants.df6US.format(freqSummaryCAngle [task][1][0]) 
							+ "	" + constants.df6US.format(freqSummaryCAngle [task][1][1])
							+ "	" + constants.df6US.format(freqSummaryCAngle [task][1][2]));
						tp.append("	" + "COM frequency	" + constants.df6US.format(freqSummaryCAngle [task][2][0]) 
							+ "	" + constants.df6US.format(freqSummaryCAngle [task][2][1])
							+ "	" + constants.df6US.format(freqSummaryCAngle [task][2][2]));
					
						
						tools2D.addFooter(tp);
						try {
						  	tp.saveAs(dir [task] + saveName + System.getProperty("file.separator") + "results.txt");							
						}catch(Exception e) {
							String out = "";
							for(int err = 0; err < e.getStackTrace().length; err++){
								out += " \n " + e.getStackTrace()[err].toString();
							}
							progress.notifyMessage("Failed to write the file path " + dir [task] + saveName + System.getProperty("file.separator") + "results.txt" + " .\nAn error occured:\n" + out, ProgressDialog.ERROR);
						}
					  	
					//save selection
					  	rm = RoiManager.getInstance();
						if (rm==null) rm = new RoiManager();
						rm.reset();
						rm.addRoi(selections [task]);
						rm.runCommand("Save", savePath + "selectedRoi.zip");	
					
					//save images	  
						progress.updateBarText("save oriented Kymograph");
					  	tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOX, 0);
					  	tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOX, 0);
					  	System.gc();
					  	tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOY, 0);
					  	tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOY, 0);
				  		System.gc();
//				  		tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOXRAW, 0);
					  	tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOXRAW, 0);
					  	System.gc();
//					  	tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOYRAW, 0);
					  	tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOYRAW, 0);
				  		System.gc();
				  		tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOMAXINTENSITY, 0);
				  		tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOMAXINTENSITY, 0);
				  		System.gc();
				  		tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOCURV, 0);
				  		tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOCURV, 0);
				  		System.gc();
				  		tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOCANGLEXY, 0);
				  		tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOCANGLEXY, 0);
				  		System.gc();
//				  		tools2D.saveOrientedKymograph(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOTANGENTANGLE, 0);
				  		tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, tools2D.NOZ, tools2D.KYMOTANGENTANGLE, 0);
				  		System.gc();				  		
				  					  		
				  		progress.updateBarText("save kymographs and images z ...");
				  		
				  		tools2D.saveTraceImage(imp, traces, encoding, savePath);	
				  		
				  		tools2D.saveOrientedKymograph(traces, xyCal, savePath, encoding, tools2D.KYMOZ, 0);
				  		tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, encoding, tools2D.KYMOZ, 0);
				  		System.gc();
				  		tools2D.saveOrientedKymograph(traces, xyCal, savePath, encoding, tools2D.KYMODZ, 0);
				  		tools2D.saveOrientedKymographAsText(traces, xyCal, savePath, encoding, tools2D.KYMODZ, 0);
				  		System.gc();
				  		
				  		tools2D.saveOrientedTraceImage(imp, traces, encoding, savePath, xyCal);
				  		System.gc();
				  		
				  		progress.updateBarText("save xy coordinates ...");
				  		
				  		tools2D.saveXYCoordinates(traces, xyCal, savePath, progress);
				  		System.gc();
				  		
					//save progress dialog log file
					  	progress.saveLog(dir [task] + saveName + System.getProperty("file.separator") +"log.txt");
					  	
				  	//finish progress dialog
					  	imp.changes = false;
					  	imp.close();
					  	
					  	progress.setBar(1.0);
						
						tasksSuccessfull [task] = true;
						
					  	traces.clear();
						System.gc();
					  	break running;		  	
				}//(end runnning)
			}catch(Exception e){
				String out = "";
				for(int err = 0; err < e.getStackTrace().length; err++){
					out += " \n " + e.getStackTrace()[err].toString();
				}
				progress.notifyMessage("Task " + (1+task) + "could not be processed... an error occured: " + out, ProgressDialog.ERROR);
			}
			
			if(progress.isStopped()) break tasking;
			progress.moveTask(task);			
		}
		
		boolean success = false;
		for(int task = 0; task < tasks; task++){
			if(tasksSuccessfull [task]){
				success = true;
			}
		}		
		
		if(success){
			tp = new TextPanel("Results Summary");
			tp.append("Summary results for processing of the following images:");
			tp.append("	" + "image ID	directory	name");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" + (task+1) + "	" + dir [task] + "	" + name [task]);
				}else{
					tp.append("	" + (task+1) + "	" + dir [task] + "	" + name [task] + "	NOT SUCCESSFULLY PROCESSED!");
				}
			}
			tp.append("");
			tp.append("#####################################################");
			tp.append("");
			
			tp.append("Average Found 1st-Peak frequencies for arc-length oriented points (X)");
			tp.append("	image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryX [task][0][0]) 
					+ "	" + constants.df6US.format(freqSummaryX [task][0][1])
					+ "	" + constants.df6US.format(freqSummaryX [task][0][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 2nd-Peak frequencies for arc-length oriented points (X)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryX [task][1][0]) 
					+ "	" + constants.df6US.format(freqSummaryX [task][1][1])
					+ "	" + constants.df6US.format(freqSummaryX [task][1][2]));
				}else{
					tp.append("	" +(task+1));
				}			
			}	
			tp.append("");
			
			tp.append("Average Found COM frequencies for arc-length oriented points (X)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryX [task][2][0]) 
					+ "	" + constants.df6US.format(freqSummaryX [task][2][1])
					+ "	" + constants.df6US.format(freqSummaryX [task][2][2]));
				}else{
					tp.append("	" +(task+1));
				}			
			}	
			tp.append("");
			
			tp.append("Average Found 1st-Peak frequencies for arc-length oriented points (Y)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryY [task][0][0]) 
					+ "	" + constants.df6US.format(freqSummaryY [task][0][1])
					+ "	" + constants.df6US.format(freqSummaryY [task][0][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 2nd-Peak frequencies for arc-length oriented points (Y)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryY [task][1][0]) 
					+ "	" + constants.df6US.format(freqSummaryY [task][1][1])
					+ "	" + constants.df6US.format(freqSummaryY [task][1][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found COM frequencies for arc-length oriented points (Y)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryY [task][2][0]) 
					+ "	" + constants.df6US.format(freqSummaryY [task][2][1])
					+ "	" + constants.df6US.format(freqSummaryY [task][2][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 1st-Peak frequencies for arc-length oriented points (Z)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryZ [task][0][0]) 
					+ "	" + constants.df6US.format(freqSummaryZ [task][0][1])
					+ "	" + constants.df6US.format(freqSummaryZ [task][0][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 2nd-Peak frequencies for arc-length oriented points (Z)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryZ [task][1][0]) 
					+ "	" + constants.df6US.format(freqSummaryZ [task][1][1])
					+ "	" + constants.df6US.format(freqSummaryZ [task][1][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found COM frequencies for arc-length oriented points (Z)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryZ [task][2][0]) 
					+ "	" + constants.df6US.format(freqSummaryZ [task][2][1])
					+ "	" + constants.df6US.format(freqSummaryZ [task][2][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 1st-Peak frequencies for arc-length oriented points (Curvature)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryCurv [task][0][0]) 
					+ "	" + constants.df6US.format(freqSummaryCurv [task][0][1])
					+ "	" + constants.df6US.format(freqSummaryCurv [task][0][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 2nd-Peak frequencies for arc-length oriented points (Curvature)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryCurv [task][1][0]) 
					+ "	" + constants.df6US.format(freqSummaryCurv [task][1][1])
					+ "	" + constants.df6US.format(freqSummaryCurv [task][1][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found COM frequencies for arc-length oriented points (Curvature)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryCurv [task][2][0]) 
					+ "	" + constants.df6US.format(freqSummaryCurv [task][2][1])
					+ "	" + constants.df6US.format(freqSummaryCurv [task][2][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 1st-Peak frequencies for arc-length oriented points (Curvature angle)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryCAngle [task][0][0]) 
					+ "	" + constants.df6US.format(freqSummaryCAngle [task][0][1])
					+ "	" + constants.df6US.format(freqSummaryCAngle [task][0][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found 2nd-Peak frequencies for arc-length oriented points (Curvature angle)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryCAngle [task][1][0]) 
					+ "	" + constants.df6US.format(freqSummaryCAngle [task][1][1])
					+ "	" + constants.df6US.format(freqSummaryCAngle [task][1][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			tp.append("Average Found COM frequencies for arc-length oriented points (Curvature angle)");
			tp.append("	" + "image ID	1st third of flagellum	2nd third of flagellum	3rd third of flagellum");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) + "	" + constants.df6US.format(freqSummaryCAngle [task][2][0]) 
					+ "	" + constants.df6US.format(freqSummaryCAngle [task][2][1])
					+ "	" + constants.df6US.format(freqSummaryCAngle [task][2][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			tp.append("");
			
			
			tp.append("Average found frequencies for angle theta");
			tp.append("	" + "image ID"
					+ "	" + "Primary freq. (2D)" + "	" + "Secondary freq. (2D)" + "	" + "COM freq. (2D)");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) 
							+ "	" + constants.df6US.format(freqSummaryTheta2D [task][0])
							+ "	" + constants.df6US.format(freqSummaryTheta2D [task][1])
							+ "	" + constants.df6US.format(freqSummaryTheta2D [task][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}	
			
			tp.append("Average found head rotation frequencies");
			tp.append("	" + "image ID"
					+ "	" + "Primary freq. (max position)" + "	" + "Secondary freq. (max position)" + "	" + "COM freq. (max position)"
					+ "	" + "Primary freq. (max intensity)" + "	" + "Secondary freq. (max intensity)" + "	" + "COM freq. (max intensity)"
					+ "	" + "Primary freq. (pos.-int.-angle)" + "	" + "Secondary freq. (pos.-int.-angle)" + "	" + "Secondary freq. (pos.-int.-angle)");
			for(int task = 0; task < tasks; task++){
				if(tasksSuccessfull [task]){
					tp.append("	" +(task+1) 
							+ "	" + constants.df6US.format(freqSummaryHrMaxPos [task][0])
							+ "	" + constants.df6US.format(freqSummaryHrMaxPos [task][1])
							+ "	" + constants.df6US.format(freqSummaryHrMaxPos [task][2])
							+ "	" + constants.df6US.format(freqSummaryHrMaxInt [task][0])
							+ "	" + constants.df6US.format(freqSummaryHrMaxInt [task][1])
							+ "	" + constants.df6US.format(freqSummaryHrMaxInt [task][2])
							+ "	" + constants.df6US.format(freqSummaryHrAngle [task][0])
							+ "	" + constants.df6US.format(freqSummaryHrAngle [task][1])
							+ "	" + constants.df6US.format(freqSummaryHrAngle [task][2]));
				}else{
					tp.append("	" +(task+1));
				}	
			}				
			tools2D.addFooter(tp);
			
			try {
				tp.saveAs(homePath + System.getProperty("file.separator") + "SpermQ_Summary_" + constants.dateName.format(new Date()) + ".txt");					
			}catch(Exception e) {
				String out = "";
				for(int err = 0; err < e.getStackTrace().length; err++){
					out += " \n " + e.getStackTrace()[err].toString();
				}
				progress.notifyMessage("Failed to save the file " + homePath + System.getProperty("file.separator") + "SpermQ_Summary_" + constants.dateName.format(new Date()) + ".txt " + " due to an error:\n" + out, ProgressDialog.ERROR);
			}
			
			System.gc();
			done = true;
			new WaitForUserDialog("All tasks have been processed. A summary file has been saved at\n" + homePath + "!").show();
		}		
	}
}
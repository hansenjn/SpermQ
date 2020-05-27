/***===============================================================================
 ProgressDialog.java Version 20161129

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 
 See the GNU General Public License for more details.
 
 Parts of the code were inherited from MotiQ (https://github.com/hansenjn/MotiQ).

 Copyright (C) 2016 Jan N Hansen 
  
 For any questions please feel free to contact me (jan.hansen@uni-bonn.de).

==============================================================================**/

package spermQ.jnh.support;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.SwingConstants;

public class ProgressDialog extends javax.swing.JFrame implements ActionListener{	
	private static final long serialVersionUID = 1L;	//TODO necessary?
	public static final int ERROR = 0;
	public static final int NOTIFICATION = 1;
	public static final int LOG = 2;
	
	private String dataLeft [], dataRight[], notifications [], log [];
	private boolean notificationsAvailable = false, errorsAvailable = false;
	private int task, tasks;
	
	private JPanel bgPanel;
	private JScrollPane jScrollPaneLeft, jScrollPaneRight, jScrollPaneBottom;
	private JList ListeLeft, ListeRight, ListeBottom;
	
	private JProgressBar progressBar = new JProgressBar();
	private double taskFraction = 0.0;
	
	private boolean stopped = false;
	
	public ProgressDialog(String [] taskList, int newTasks) {
		super();
		initGUI();
		dataLeft = taskList.clone();
		tasks = newTasks;
		for(int i = 0; i < tasks; i++){
			if(dataLeft[i]!=""){
				dataLeft [i] = (i+1) + ": " + dataLeft [i]; 
			}			
		}
		ListeLeft.setListData(dataLeft);
		taskFraction = 0.0;
		task = 1;
	}
	
	private void initGUI() {
		int prefXSize = 600, prefYSize = 500;
		this.setMinimumSize(new java.awt.Dimension(prefXSize, prefYSize+40));
		this.setSize(prefXSize, prefYSize+40);			
		this.setTitle("MultiTaskManager - by JN Hansen (\u00a9 2016)");
//		this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		//Surface
			bgPanel = new JPanel();
			bgPanel.setLayout(new BoxLayout(bgPanel, BoxLayout.Y_AXIS));
			bgPanel.setVisible(true);
			bgPanel.setPreferredSize(new java.awt.Dimension(prefXSize,prefYSize-20));
			{//TOP: Display tasks left, and tasks that were run right
				int subXSize = prefXSize, subYSize = 200;
				JPanel topPanel = new JPanel();
				topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
				topPanel.setVisible(true);
				topPanel.setPreferredSize(new java.awt.Dimension(subXSize,subYSize));
				{
					JPanel imPanel = new JPanel();
					imPanel.setLayout(new BorderLayout());
					imPanel.setVisible(true);
					imPanel.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)),subYSize));
					{
						JLabel spacer = new JLabel("Remaining files to process:",SwingConstants.LEFT);
						spacer.setMinimumSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-20),60));
						spacer.setVisible(true);
						imPanel.add(spacer,BorderLayout.NORTH); 
					}
					{
						jScrollPaneLeft = new JScrollPane();
						jScrollPaneLeft.setHorizontalScrollBarPolicy(30);
						jScrollPaneLeft.setVerticalScrollBarPolicy(20);
						jScrollPaneLeft.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-10), subYSize-60));
						imPanel.add(jScrollPaneLeft,BorderLayout.CENTER); 
						{
							ListModel ListeModel = new DefaultComboBoxModel(new String[] { "" });
							ListeLeft = new JList();
							jScrollPaneLeft.setViewportView(ListeLeft);
							ListeLeft.setModel(ListeModel);
						}
					}	
					topPanel.add(imPanel);
				}
				{
					JPanel imPanel = new JPanel();
					imPanel.setLayout(new BorderLayout());
					imPanel.setVisible(true);
					imPanel.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)),subYSize));
					{
						JLabel spacer = new JLabel("Processed files:",SwingConstants.LEFT);
						spacer.setMinimumSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-20),60));
						spacer.setVisible(true);
						imPanel.add(spacer,BorderLayout.NORTH); 
					}
					{	
						jScrollPaneRight = new JScrollPane();
						jScrollPaneRight.setHorizontalScrollBarPolicy(30);
						jScrollPaneRight.setVerticalScrollBarPolicy(20);
						jScrollPaneRight.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-10), subYSize-60));
						imPanel.add(jScrollPaneRight,BorderLayout.CENTER); 
						{
							ListModel ListeModel = new DefaultComboBoxModel(new String[] { "" });
							ListeRight = new JList();
							jScrollPaneRight.setViewportView(ListeRight);
							ListeRight.setModel(ListeModel);
						}
					}
					topPanel.add(imPanel);
				}				
				bgPanel.add(topPanel);
			}
			{
				JPanel spacer = new JPanel();
				spacer.setMaximumSize(new java.awt.Dimension(prefXSize,10));
				spacer.setVisible(true);
				bgPanel.add(spacer);
			}
			{
				progressBar = new JProgressBar();
				progressBar = new JProgressBar(0, 100);
				progressBar.setPreferredSize(new java.awt.Dimension(prefXSize,40));
				progressBar.setStringPainted(true);
				progressBar.setValue(0);
				progressBar.setString("no analysis started!");
				bgPanel.add(progressBar);	
			}
			{
				JPanel spacer = new JPanel();
				spacer.setMaximumSize(new java.awt.Dimension(prefXSize,10));
				spacer.setVisible(true);
				bgPanel.add(spacer);
			}
			{
				JPanel imPanel = new JPanel();
				imPanel.setLayout(new BorderLayout());
				imPanel.setVisible(true);
				imPanel.setPreferredSize(new java.awt.Dimension(prefXSize,140));
				{
					JLabel spacer = new JLabel("Notifications:", SwingConstants.LEFT);
					spacer.setMinimumSize(new java.awt.Dimension(prefXSize,40));
					spacer.setVisible(true);
					imPanel.add(spacer, BorderLayout.NORTH);
				}
				{	
					jScrollPaneBottom = new JScrollPane();
					jScrollPaneBottom.setHorizontalScrollBarPolicy(30);
					jScrollPaneBottom.setVerticalScrollBarPolicy(20);
					jScrollPaneBottom.setPreferredSize(new java.awt.Dimension(prefXSize, 100));
					imPanel.add(jScrollPaneBottom, BorderLayout.CENTER);
					{
						ListModel ListeModel = new DefaultComboBoxModel(new String[] { "" });
						ListeBottom = new JList();
						jScrollPaneBottom.setViewportView(ListeBottom);
						ListeBottom.setModel(ListeModel);
					}
				}
				bgPanel.add(imPanel);
			}
			getContentPane().add(bgPanel);		
	}
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		Object eventQuelle = ae.getSource();
//		if (eventQuelle == abortButton){
//			abort = true;
////			updateDisplay();
//		}	
	}
	
	public void moveTask(int i){		
		if(dataRight == null){
			dataRight = new String [2];
			dataRight [0] = "" + dataLeft[0];
			
			String [] dataLeftCopy = dataLeft.clone();
			dataLeft = new String [dataLeft.length-1];
			for(int j = 1; j < dataLeftCopy.length; j++){
				dataLeft[j-1] = dataLeftCopy[j];
			}
		}else if(i==(tasks-1)){
			String [] dataRightCopy = dataRight.clone();
			dataRight = new String [dataRight.length+1];
			for(int j = 0; j < dataRightCopy.length; j++){
				dataRight[j+1] = dataRightCopy[j];
			}
			dataRight[0] = ""+dataLeft[0];			
			dataLeft = new String [2];
		}else{
			String [] dataRightCopy = dataRight.clone();
			dataRight = new String [dataRight.length+1];
			for(int j = 0; j < dataRightCopy.length; j++){
				dataRight [j+1] = dataRightCopy[j];
			}
			dataRight [0] = "" + dataLeft [0];
						
			String [] dataLeftCopy = dataLeft.clone();
			dataLeft = new String [dataLeft.length-1];
			for(int j = 1; j < dataLeftCopy.length; j++){
				dataLeft [j-1] = dataLeftCopy[j];
			}			
		}	
		ListeLeft.setListData(dataLeft);
		ListeRight.setListData(dataRight);
		jScrollPaneLeft.updateUI();
		jScrollPaneRight.updateUI();
		bgPanel.updateUI();
		
		if(task == tasks){
			if(errorsAvailable){
				replaceBarText("processing done but some tasks failed (see notifications)!");
				progressBar.setValue(100); 		
				progressBar.setStringPainted(true);
				progressBar.setForeground(Color.red);
			}else if(notificationsAvailable){
				replaceBarText("processing done, but some notifications are available!");
				progressBar.setValue(100); 
				progressBar.setStringPainted(true);
				progressBar.setForeground(new Color(255,130,0));
			}else{
				replaceBarText("analysis done!");
				progressBar.setStringPainted(true);
				progressBar.setForeground(new Color(0,140,0));
			}
		}else{
			taskFraction = 0.0;
			task++;
		}
	}
	
	public void notifyMessage(String message, int type){
		if(type == ERROR){
			errorsAvailable = true;
		}else if(type == NOTIFICATION){
			notificationsAvailable = true;
		}
		
		if(type == ERROR || type == NOTIFICATION){
			if(notifications==null ){
				notifications = new String [1];
				notifications [0] = message;
			}else{
				String [] notificationsCopy = notifications.clone();
				notifications = new String [notifications.length+1];
				for(int j = 0; j < notificationsCopy.length; j++){
					notifications[j+1] = notificationsCopy[j];
				}
				notifications [0] = message;
			}
		}else{
			if(log==null ){
				log = new String [1];
				log [0] = message;
			}else{
				String [] logCopy = log;
				log = new String [log.length+1];
				for(int j = 0; j < logCopy.length; j++){
					log[j+1] = logCopy[j];
				}
				log [0] = message;
			}
		}
		
		if(notifications != null && log == null){
			ListeBottom.setListData(notifications);
		}else if(notifications == null && log != null){
			ListeBottom.setListData(log);
		}else if(notifications != null && log != null){
			String [] listData = new String [notifications.length + log.length];
			for(int i = 0; i < notifications.length; i++){
				listData [i] = notifications [i];
			}
			for(int i = 0; i < log.length; i++){
				listData [notifications.length + i] = log [i];
			}
			ListeBottom.setListData(listData);
		}		
		bgPanel.updateUI();
	}
	
	public void addToBar(double addFractionOfTask){
		taskFraction += addFractionOfTask;
		if(taskFraction >= 1.0){
			taskFraction = 0.9;
		}
		progressBar.setValue((int)Math.round(((double)(task-1)/tasks)*100.0+taskFraction*(100/tasks)));
	}
	
	public void setBar(double fractionOfTask){
		taskFraction = fractionOfTask;
		if(taskFraction > 1.0){
			taskFraction = 0.9;
		}
		progressBar.setValue((int)Math.round(((double)(task-1)/tasks)*100.0+taskFraction*(100/tasks)));
	}
	
	public void updateBarText(String text){
		progressBar.setString("Task " + task + "/" + tasks + ": " + text);
	}
	
	public void replaceBarText(String text){			
		progressBar.setString(text);
	}
	
	public void stopProcessing(){
		stopped = true;
	}
	
	public boolean isStopped(){
		return stopped;
	}
	
	public void saveLog(String path){
		if(log!=null){
			final SimpleDateFormat fullDate = new SimpleDateFormat("yyyy-MM-dd	HH:mm:ss");
			File file = new File(path);
			try {
				if (!file.exists()) {
					file.createNewFile();
				}
				
				FileWriter fw = new FileWriter(file.getAbsoluteFile());
				PrintWriter pw = new PrintWriter(fw);
				pw.println("Logfile created on:	" + fullDate.format(new Date()));
				pw.println("");
				for(int i = 0; i < log.length; i++){
					pw.println(log[i]);
				}			
				pw.close();
			}catch (IOException e) {
				e.printStackTrace();
//				IJ.error(e.getMessage());
				this.notifyMessage(e.toString(),ProgressDialog.ERROR);
			}
		}		
	}
	
	public void clearLog(){
		log = null;
		if(notifications != null){
			ListeBottom.setListData(notifications);
		}else{
			ListeBottom.setListData(new String []{""});
		}
		bgPanel.updateUI();
	}
}
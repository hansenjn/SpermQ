package spermQ.jnh.support;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/**
These tools are free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

See the GNU General Public License for more details.

Parts of the code were inherited from MotiQ (https://github.com/hansenjn/MotiQ).

Copyright (C) 2016-2020 Jan N Hansen 
  
For any questions please feel free to contact me (jan.hansen@uni-bonn.de).

**/


public class OpenFilesDialog extends javax.swing.JFrame implements ActionListener{
	public LinkedList<File> filesToOpen = new LinkedList<File>();
	public boolean done = false;
	boolean dirsaved = false;
	File saved;// = new File(getClass().getResource(".").getFile());
	JMenuBar jMenuBar1;
	JMenu jMenu3, jMenu5;
	JSeparator jSeparator2;
	JPanel bgPanel;
	JScrollPane jScrollPane1;
	JList Liste1;
	JButton loadButton, removeButton, goButton, loadListButton;
	
	public OpenFilesDialog() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		int prefXSize = 600, prefYSize = 400;
		this.setMinimumSize(new java.awt.Dimension(prefXSize, prefYSize+40));
		this.setSize(prefXSize, prefYSize+40);			
		this.setTitle("Multi-Task-Manager - by JN Hansen (\u00a9 2016)");
		//Surface
			bgPanel = new JPanel();
			bgPanel.setLayout(new BoxLayout(bgPanel, BoxLayout.Y_AXIS));
			bgPanel.setVisible(true);
			bgPanel.setPreferredSize(new java.awt.Dimension(prefXSize,prefYSize-20));
			{
				jScrollPane1 = new JScrollPane();
				jScrollPane1.setHorizontalScrollBarPolicy(30);
				jScrollPane1.setVerticalScrollBarPolicy(20);
				jScrollPane1.setPreferredSize(new java.awt.Dimension(prefXSize-10, prefYSize-60));
				bgPanel.add(jScrollPane1);
				{
					Liste1 = new JList();
					jScrollPane1.setViewportView(Liste1);
					Liste1.setModel(new DefaultComboBoxModel(new String[] { "" }));
				}
				{
					JPanel spacer = new JPanel();
					spacer.setMaximumSize(new java.awt.Dimension(prefXSize,10));
					spacer.setVisible(true);
					bgPanel.add(spacer);
				}
				{
					JPanel bottom = new JPanel();
					bottom.setLayout(new BoxLayout(bottom, BoxLayout.X_AXIS));
					bottom.setMaximumSize(new java.awt.Dimension(prefXSize,10));
					bottom.setVisible(true);
					bgPanel.add(bottom);
					int locHeight = 40;
					int locWidth3 = prefXSize/4-60;
					{
						loadButton = new JButton();
						loadButton.addActionListener(this);
						loadButton.setText("add file(s)");
						loadButton.setMinimumSize(new java.awt.Dimension(locWidth3,locHeight));
						loadButton.setVisible(true);
						loadButton.setVerticalAlignment(SwingConstants.BOTTOM);
						bottom.add(loadButton);
					}
					{
						loadListButton = new JButton();
						loadListButton.addActionListener(this);
						loadListButton.setText("import paths from textfile(s)");
						loadListButton.setMinimumSize(new java.awt.Dimension(locWidth3,locHeight));
						loadListButton.setVisible(true);
						loadListButton.setVerticalAlignment(SwingConstants.BOTTOM);
						bottom.add(loadListButton);
					}
					{
						removeButton = new JButton();
						removeButton.addActionListener(this);
						removeButton.setText("remove selected file(s)");
						removeButton.setMinimumSize(new java.awt.Dimension(locWidth3,locHeight));
						removeButton.setVisible(true);
						removeButton.setVerticalAlignment(SwingConstants.BOTTOM);
						bottom.add(removeButton);
					}	
					{
						goButton = new JButton();
						goButton.addActionListener(this);
						goButton.setText("start processing");
						goButton.setMinimumSize(new java.awt.Dimension(locWidth3,locHeight));
						goButton.setVisible(true);
						goButton.setVerticalAlignment(SwingConstants.BOTTOM);
						bottom.add(goButton);
					}						
				}	
			}
			getContentPane().add(bgPanel);		
	}
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		Object eventQuelle = ae.getSource();
		if (eventQuelle == loadButton){
			JFileChooser chooser = new JFileChooser();
			chooser.setPreferredSize(new Dimension(600,400));
			if(dirsaved){				
				chooser.setCurrentDirectory(saved);
			}			
			chooser.setMultiSelectionEnabled(true);
			Component frame = null;
			chooser.showOpenDialog(frame);
			File[] files = chooser.getSelectedFiles();
			for(int i = 0; i < files.length; i++){
				filesToOpen.add(files[i]);
				saved = files[i];
				dirsaved=true;
			}			
			updateDisplay();
		}
		if (eventQuelle == removeButton){
			int[] indices = Liste1.getSelectedIndices();
			for(int i = indices.length-1; i >=0; i--){
				filesToOpen.remove(indices[i]);
			}
			updateDisplay();
		}
		if (eventQuelle == goButton){
			done=true;
			dispose();
		}	
		if (eventQuelle == loadListButton){
			JFileChooser chooser = new JFileChooser();
			chooser.setPreferredSize(new Dimension(600,400));
			if(dirsaved){				
				chooser.setCurrentDirectory(saved);
			}			
			chooser.setMultiSelectionEnabled(true);
			Component frame = null;
			chooser.showOpenDialog(frame);
			File[] files = chooser.getSelectedFiles();
			FileReader fr;
			BufferedReader br;
			String line;
			for(int l = 0; l < files.length; l++){
				try {
					fr = new FileReader(files[l]);
					br = new BufferedReader(fr);
					copyPaste: while (true) {
						try {
							line = br.readLine();
							if(line == null){
								break copyPaste;
							}
							File newFile = new File(line);
							filesToOpen.add(newFile);		
							updateDisplay();
						} catch (Exception e) {
							String out = "";
							for (int err = 0; err < e.getStackTrace().length; err++) {
								out += " \n " + e.getStackTrace()[err].toString();
							}
							System.out.println(out);
							e.printStackTrace();
							break copyPaste;
						}
					}
					br.close();
					fr.close();
				}
				catch(Exception e) {
				}	
				saved = files[l];
			}			
		}		
	}
	
	public void updateDisplay(){
		String resultsString [] = new String [filesToOpen.size()];
		for(int i = 0; i < filesToOpen.size(); i++){
			resultsString [i] = (i+1) + ": " + filesToOpen.get(i).getName();
		}
		Liste1.setListData(resultsString);
	}
}

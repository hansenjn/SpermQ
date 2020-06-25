# SpermQ
An ImageJ plugin to analyze the flagellar beat of sperm and sperm steering. Download the latest release [here](https://github.com/hansenjn/SpermQ/releases). 

Copyright (C) 2017-2020: Jan N. Hansen, Sebastian Rassmann, Jan F. Jikeli, and Dagmar Wachten; research group Biophysical Imaging, Institute of Innate Immunity, Bonn, Germany (http://www.iii.uni-bonn.de/en/wachten_lab/).

Funding: DFG priority program SPP 1726 "Microswimmers".

This software is part of the following publication: 

Hansen, J.N.; Rassmann, S.; Jikeli, J.F.; Wachten, D. SpermQ–A Simple Analysis Software to Comprehensively Study Flagellar Beating and Sperm Steering. Cells 2019, 8, 10. doi: [10.3390/cells8010010](https://doi.org/10.3390/cells8010010).

## Citing SpermQ
Please cite the SpermQ publication when presenting / publishing data that were achieved by using SpermQ:

Hansen, J.N.; Rassmann, S.; Jikeli, J.F.; Wachten, D. SpermQ–A Simple Analysis Software to Comprehensively Study Flagellar Beating and Sperm Steering. Cells 2019, 8, 10. doi: [10.3390/cells8010010](https://doi.org/10.3390/cells8010010).

## How to use and apply SpermQ?
A user guide for SpermQ analysis is available [here](https://github.com/hansenjn/SpermQ/blob/master/Manual/SpermQ%20Manual.pdf). For additional help on selecting the optimal SpermQ settings and setting up a good SpermQ analysis pipeline, please contact jan.hansen(at)uni-bonn.de.

### The SpermQ workflow in a nutshell
#### Imaging
SpermQ processes 2D time-lapse images (supplied as .tif-stacks), in which SpermQ detects flagella/motile cilia as bright pixels (high intensity values) on dark background pixels (low intensity values), ideally with high contrast. This can be achieved using dark-field or fluorescence microscopy. To analyze mouse or human sperm flagella beating, we recommed to record images with a simple dark-field microscope, a camera (frame rate should be at least 200 fps for mouse, 500 fps for human sperm), a 10x or 16x magnification for mouse or a 20x or 32x magnification for human sperm, a recording time of at least 1 sec (rather 2 sec).

However, you may also use (if already recorded or no other microscope available) phase contrast or bright-field microscopy to acquire the images - in this case there will be some additional preprocessing steps necessary before image analysis. Most importantly, make sure that in your recordings multiple sperm do not overlap or come close in the image (eventually dilute the sperm solution). In addition it must be possible to distinguish the sperm cell at all points of the flagellum from the background.

#### Image analysis
Image analysis is performed in ImageJ (which is freely accessible, see also System requirements below) using ImageJ plugins. 
In principle you should consider three steps of the analysis (please read the [User Guide](https://github.com/hansenjn/SpermQ/blob/master/Manual/SpermQ%20Manual.pdf) for detailed explanations on how to perform the workflow):
1. Preprocessing the data, e.g. background reduction, evtl. smoothing -> this allows to get an image where the flagellum is best distinguishable (with high contrast) from the background. You may use the ImageJ plugin [SpermQ Preparator](https://github.com/hansenjn/SpermQ_Preparator) to this end; SpermQ Evaluator also allows batch processing allows to let run an entire data set while you do not need the computer, e.g. during the night.
2. Analyze the preprocessed files with the ImageJ plugin SpermQ. SpermQ can also perform batch processing. Depending on the image resolution and the number of frames per recording it can take some time for each flagellum (3 or 4 min up to one hour). Batch processing allows you to let run an entire data set while you do not need the computer, e.g. during the night.
3. Convolve data of many flagella (or only one) in [SpermQ Evaluator](https://github.com/IIIImaging/SpermQ_Evaluator) (a pure java tool, does not need imageJ). SpermQ Evaluator produces overview tables and PDF files with plots for each analyzed flagellum.

### System requirements
SpermQ consists of ImageJ plugins and a pure java tool. 

#### Software requirements
Performing the analysis pipeline requires the installation of
- Java™ by Oracle (tested on Version 8, Update 231)
- [ImageJ](https://imagej.net/Downloads) (tested on versions 1.51r, 1.51u, 1.52i, and 1.53a).

#### Hardware requirements
ImageJ and java do not require any specific hardware and can also run on low-performing computers. However, a RAM is required that allows to load one image sequence that you aim to analyze into your RAM at least once, ideally twice or multiple times. ImageJ does not require any specific graphics card. The speed of the analysis depends on the processor speed.

#### Operating system
The ImageJ plugins and Java software were developed and tested on Windows 8.1 and Mac OS X Catalina (Version 10.15.5).
ImageJ and Java is also available for Linux, where the ImageJ plugins and Java software in theory can be easily run, too.

## Important LICENSE note
The newly developed software is licensed under GNU General Public License v3.0. However, this software includes packages derived from others, for which different licenses may apply. The different licenses and the authors of theses code parts are clearly stated in the headers of the respective classes. This applies to the following packages\classes:
- edu.emory.mathcs.jtransforms.fft\DoubleFFT_1D.java & edu.emory.mathcs.utils\ConcurrencyUtils.java (MPL 1.1/GPL 2.0/LGPL 2.1, Mozilla Public License Version 1.1, author: Piotr Wendykier)
- AnalyzeSkeleton & Skeletonize3D (GNU General Public License, http://www.gnu.org/licenses/gpl.txt, author: Ignacio Arganda-Carreras)

## Publications using SpermQ
- Olstad, E.W., Ringers, C., et al. Ciliary Beating Compartmentalizes Cerebrospinal Fluid Flow in the Brain and Regulates Ventricular Development.
*Current Biology* 2019, Volume 29, Issue 2, Pages 229-241.e6, ISSN 0960-9822, https://doi.org/10.1016/j.cub.2018.11.059. (http://www.sciencedirect.com/science/article/pii/S0960982218315896)
- Raju, D.N., Hansen, J.N., Rassmann, S., et al., Cyclic Nucleotide-Specific Optogenetics Highlights Compartmentalization of the Sperm Flagellum into cAMP Microdomains. *Cells* 2019, 8, 648. https://doi.org/10.3390/cells8070648. (https://www.mdpi.com/2073-4409/8/7/648)
- Striggow, F., Medina‐Sánchez, M., Auernhammer, G. K., Magdanz, V., Friedrich, B. M., Schmidt, O. G., Sperm‐Driven Micromotors Moving in Oviduct Fluid and Viscoelastic Media. *Small* 2020, 16, 2000213. https://doi.org/10.1002/smll.202000213. (https://onlinelibrary.wiley.com/doi/full/10.1002/smll.202000213)

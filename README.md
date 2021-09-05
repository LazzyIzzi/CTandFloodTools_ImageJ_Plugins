# MuMassCalculator_ImageJ
ImageJ plugins implementing MuMassCalculator methods.

These plugins require MuMassCalculator to run.

See https://github.com/LazzyIzzi/MuMassCalculator for additional information

**Installation**

Download MuMassCalculator_J8Lib.jar to the ImageJ plugins/jars folder

Download Tomography_Tools.jar to the ImageJ plugins folder

Start ImageJ to install the plugins.

A MuMassCalculator jar file, JavaDocs and sample ImageJ PlugIns are also available at

https://drive.google.com/drive/folders/1xr8dBjpwd2bo6ZqFmwoKvBBDUoqaK0Bg?usp=sharing

**Use**

**_Xray_Calculator_**: Does not operate on images. It fetches and displays x-ray absorption spectra and absorption edge data from the MuMassCalculator XCOM data base.  It is intended to provide guidance for other imaging operations.

**_Beam_Hardening_Estimator_**: Interactively plots the bremsstrahlung x-ray intensity spectra for a simplified model x-ray CT scanner, i.e the change in the intensity distribution along the source->filter->sample->detector path. A simplified "ideal" detector counts only photons absorbed by a scintillator layer.  Real detectors are beyond the scope of this tool. Estimates of the photon efficiency and degree of beam-hardening are logged for each scanner configuration.

**Note:** All projectors described below use a simulated linear detector array, Curved detector arrays are not supported.

**_Parallel_MuLin_Scan_**: Creates a parallel projection sinogram from the inscribed circular region of a 2D 32-bit image with equal width and height.  The image pixel values are typically the linear attenuation coefficients of the imaged materials.

**_Fan_MuLin_Scan_**: Creates a fan beam projection sinogram from the inscribed circular region of a 2D 32-bit image with equal width and height using user defined projection geometry.  The image pixel values are typically the linear attenuation coefficients of the imaged materials.

**_Parallel_Bremsstrahlung_Scan_**: Creates a simulated polychromatic parallel projection sinogram from the inscribed circular region of a 2D "tagged" 32-bit image with equal width and height.  A polychromatic x-ray source is simulated by combining projections taken at equally spaced energy steps within a user-specified range. At each step, the "tagged" pixel values are integers corresponding to a user supplied list of component compositions and densities. See the Materials.txt and Materials.csv files for examples. 

**_Fan_Bremsstrahlung_Scan_**: Creates a simulated polychromatic fan beam projection sinogram from the inscribed circular region of a 2D "tagged" 32-bit image with equal width and height using user defined projection geometry. A polychromatic x-ray source is simulated by combining projections taken at equally spaced energy steps within a user-specified range. At each step, the "tagged" pixel values are integers corresponding to a user supplied list of component compositions and densities. See the Materials.txt and Materials.csv files for examples. 

_These plugins are intended for use in testing reconstruction software and for evaluating methods for the correction of beam-hardening and other imaging artifacts for quantitative compositional analysis using measured/corrected x-ray linear attenuation coefficients._

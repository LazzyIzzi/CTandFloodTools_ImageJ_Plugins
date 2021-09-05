//package Scanner_Sims;

/*
 * This plugin simulates a Fan beam CT scan from 0 to 360 degrees
 * of a segmented 2D image using a conventional x-ray source and a scintillation
 * detector.
 * 
 *  The input image must be segmented into N components. The pixel values of each component must be
 *  set to an integer (tagged) beginning 1, corresponding to a composition and density in a
 *  Materials text file.
 *
 * A Materials file is a simple text file containing
 * a formula in the Atom1:Count1:atom2:Count2 format ,comma, followed by a density in gmPerCC.
 * One formula,density pair per line.
 * The first material is assigned to tag=1
 * The second in assigned to tag=2 etc.
 * e.g. for an image of Sandstone with Calcite cements
 * Ca:1:C:1:O:3, 2.71
 * Si:1:O:2, 2.53
 * 
 * This may seem a bit awkward but it allows the user to create a libraries of tagged components
 * so they won't need to be typed in every time.  Excel csv files will work for this purpose.
 * 
 * The tags are used to convert the image to linear attenuation at each energy in the
 * polychromatic scan.
 *  
 * The spectral intensity of the source is estimated using the Kramers equation.
 * The transmission of the filter, the absorption of the image (sample) and the
 * absorption of the detector are computed using Beer-Lambert.
 * 
 * Polychromatic x-rays are simulated by combining the sample signals taken at
 * increments of x-ray energy.
 * The attenuation of each component is computed from the components formula 
 * and density and the x-ray energy.
 * 
 * A CT scan is simulated by rotating the source detector about the center of the image and p
 * collecting projections to form a sinogram.
 * 
 * This version defines the scanner geometry in centimeters.
 * Attempts to serialize the dialog parameters.
 */

import ij.IJ;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.gui.*;
import ij.measure.Calibration;

import java.awt.event.*;
import java.io.File;
import java.awt.*;
import java.util.ArrayList;


import jhd.MuMassCalculator.*;
import gray.AtomData.*;
import jhd.Projection.*;
import jhd.Serialize.*;


//*******************************************************************************

public class Fan_Bremsstrahlung_Scan_Lib implements PlugInFilter , DialogListener, ActionListener
{
	//GLOBALS
	String[] targetSymb = {"Ag","Au","Cr","Cu","Mo","Rh","W"};
	String[] filterSymb = {"Ag","Al","Cu","Er","Mo","Nb","Rh","Ta"};
	
	static final String myDialogTitle = "Polychromatic Fan Beam CTscan";
	static final String mySettingsTitle = "Polychromatic_FanBeam_Params";
	GenericDialog gd = new GenericDialog(myDialogTitle);
	ImagePlus imp;
	
	//the full path to the dialog settings
	String dir = IJ.getDirectory("plugins");	
	String settingsPath = dir+ "DialogSettings" + File.separator + mySettingsTitle + ".ser";

	//Arrays to unpack Text file materials lists
	String[] matlArr;
	String[] formula;
	double[] gmPerCC;
	boolean scale16,padImage;
	
	int oldW,oldH;

	
	//Used to test formulas prior to launching the simulator
	MuMassCalculator mmc = new MuMassCalculator();
	//The class that does the simulation
	FanProjectors fanPrj = new FanProjectors();
	//The nested class containing the simulator's user supplied parameters
	FanProjectors.BremFanParams bfpSet =  new FanProjectors.BremFanParams();
	//The class used to serialize (save) the users selections
	Serializer ser = new Serializer();
	
	//*******************************************************************************
	//The plugin setup
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32;
	}

	//*******************************************************************************
	//The plugin run
	@Override
	public void run(ImageProcessor ip)
	{
		//the original image width and height
		oldW =ip.getWidth();
		oldH =ip.getHeight();
		String unit = imp.getCalibration().getUnit().toUpperCase();
		if(!unit.equals("CM"))
		{
			IJ.error("Input image pixel units must be in cm (centimeters)");
			return;
		}

		if(imp.getWidth() != imp.getHeight())
		{
			IJ.showMessage("Image must be Square. Check the PadImage Box in the next dialog");
		}
		
		//Read the saved dialog settings
		bfpSet = (FanProjectors.BremFanParams)ser.ReadSerializedObject(settingsPath);
		if(bfpSet==null)
		{
			bfpSet = GetDialogDefaultSettings();
		}
		
		if(DoDialog())
		{
			if(ValidateParams(bfpSet))
			{
				DoRoutine(bfpSet);
				ser.SaveObjectAsSerialized(bfpSet, settingsPath);
			}
		}
	}
	
	//*******************************************************************************

	private FanProjectors.BremFanParams GetDialogDefaultSettings()
	{
		FanProjectors.BremFanParams dlogSet = new FanProjectors.BremFanParams();
		dlogSet.target = "W";
		dlogSet.kv = 160;
		dlogSet.ma = 100;
		dlogSet.nBins = 20;
		dlogSet.minKV = 20;

		dlogSet.filter = "Cu";
		dlogSet.filterCM = 0.1f;
		dlogSet.filterGmPerCC = 8.41;

		//Tagged Image
		dlogSet.pixSizeCM=.001f;		
		dlogSet.matlTag=null;
		dlogSet.matlName=null;
		dlogSet.matlFormula=null;
		dlogSet.matlGmPerCC=null;

		//CT params
		dlogSet.numAng=(int)(0.5*Math.PI*imp.getWidth()*1.5f);
		dlogSet.srcToDetCM=100;
		dlogSet.magnification=1.5f;

		//Detector
		dlogSet.detFormula="Cs:1:I:1";
		dlogSet.detCM=.01;
		dlogSet.detGmPerCC=8.41;		
		scale16=false;
		
		return dlogSet;		
	}

	//*******************************************************************************

	private boolean DoDialog()
	{
		//String dir = IJ.getDirectory("plugins");
		//dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";
		
		//srcToSampCM and detPixCnt are presented for user information
		bfpSet.pixSizeCM = imp.getCalibration().pixelWidth;
		double srcToSampCM = bfpSet.srcToDetCM/bfpSet.magnification;
		int detPixCnt= (int)(imp.getWidth()*bfpSet.magnification);

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		gd.addDialogListener(this);
		//X-ray Source
		gd.setInsets(10,0,0);
		gd.addMessage("X-ray Source________________",myFont,Color.BLACK);
		gd.addChoice("Target",targetSymb,"W");
		gd.addNumericField("KV", bfpSet.kv);
		gd.addNumericField("mA", bfpSet.ma);
		gd.addNumericField("KeV Bins", bfpSet.nBins);
		gd.addNumericField("Min KeV", bfpSet.minKV);
		
		//Filter
		gd.setInsets(10,0,0);
		gd.addMessage("Source Filter________________",myFont,Color.BLACK);
		gd.addChoice("Material",filterSymb,bfpSet.filter);
		gd.addNumericField("Thickness(cm)", bfpSet.filterCM);
		
		//Materials
		gd.setInsets(10,0,0);
		gd.addMessage("Material Tags________________",myFont,Color.BLACK);
		gd.setInsets(10,50,0);
		gd.addButton("Load Materials File", this);
		gd.setInsets(10,50,0);
		gd.addButton("View/Edit Materials", this);
				
		//CT
		gd.setInsets(10,0,0);
		gd.addMessage("360 degree Scan______________",myFont,Color.BLACK);
		gd.addNumericField("Suggested View Angles", bfpSet.numAng);
		gd.addNumericField("Source to Detector(cm):", bfpSet.srcToDetCM);
		gd.addNumericField("Magnification:", bfpSet.magnification);
		//Info
		gd.setInsets(10,0,0);
		gd.addMessage("Axis to Detector = " + String.format("%.3f" + " cm", bfpSet.srcToDetCM - srcToSampCM ));
		gd.setInsets(0,0,0);
		gd.addMessage("Detector Pixel Count = " + detPixCnt);
		//Detector
		gd.setInsets(10,0,0);
		gd.addMessage("Detector___________________",myFont,Color.BLACK);
		gd.addStringField("Formula", bfpSet.detFormula);
		gd.addNumericField("Thickness(cm)", bfpSet.detCM);
		gd.addNumericField("Density(gm/cc)", bfpSet.detGmPerCC);
		gd.addCheckbox("Scale to 16-bit proj", scale16);
		gd.addCheckbox("Pad Image", padImage);
		//gd.setOKLabel("Not OK");
     	 		
		gd.showDialog();

		if (gd.wasCanceled())
		{
			return false;
		}
		else
		{
			GetSelections(gd);
			return true;
		}	
	}
	
	//*******************************************************************************

	private boolean ValidateParams(FanProjectors.BremFanParams bfpSet)
	{
		//Test the formulas
		ArrayList<AtomData> formula;
		formula = mmc.createFormulaList(bfpSet.target);
		if(formula==null) {IJ.error(bfpSet.target + " Is not a valid target material"); return false;}
		formula = mmc.createFormulaList(bfpSet.filter);
		if(formula==null) {IJ.error(bfpSet.filter + " Is not a valid filter material"); return false;}
		
		if(bfpSet.matlFormula==null){IJ.error("Missing Formulas"); return false;}
		if(bfpSet.matlGmPerCC==null){IJ.error("Missing Densities"); return false;}
		
		for(int i=1;i< bfpSet.matlFormula.length;i++)
		{
			if(bfpSet.matlGmPerCC[i] <= 0){IJ.error("Material 1 Density " + bfpSet.matlGmPerCC[i] + " Cannot be negative"); return false;}
			if(bfpSet.matlFormula[i] == null){IJ.error("Missing Formula at item " + i); return false;}
			formula = mmc.createFormulaList(bfpSet.matlFormula[i]);
			if(formula==null) {IJ.error(bfpSet.matlFormula[i] + " Is not a valid  material"); return false;}			
		}
		
		formula = mmc.createFormulaList(bfpSet.detFormula);
		if(formula==null) {IJ.error(bfpSet.detFormula + " Is not a valid detector material"); return false;}
		
		//Test the numbers
		if(bfpSet.kv < bfpSet.minKV){IJ.error("Source KV " + bfpSet.kv + " Must be greater than " + bfpSet.minKV + "KV"); return false;}
		if(bfpSet.kv <=0){IJ.error("Source KV " + bfpSet.kv + " Must be greater than 0 KV"); return false;}
		if(bfpSet.ma <=0){IJ.error("Source mA " + bfpSet.ma + " Must be greater than 0 mA"); return false;}
		if(bfpSet.nBins <=0){IJ.error("Bin Count " + bfpSet.nBins + " Must be greater than 0"); return false;}
		if(bfpSet.minKV > bfpSet.kv){IJ.error("Source minMV " + bfpSet.minKV + " Must be less than " + bfpSet.kv + "KV"); return false;}
		if(bfpSet.filterCM < 0){IJ.error("Filter Thickness " + bfpSet.filterCM + " Cannot be negative"); return false;}
		if(bfpSet.filterGmPerCC <= 0){IJ.error("Filter Density " + bfpSet.filterGmPerCC + " Cannot be negative"); return false;}
		
		if(bfpSet.numAng < 1){IJ.error("Number of angles " + bfpSet.numAng + " Cannot be negative or zero"); return false;}
		if(bfpSet.detCM <= 0){IJ.error("Detector Thickness " + bfpSet.detCM + " Cannot be negative"); return false;}
		if(bfpSet.detGmPerCC <= 0){IJ.error("Detector Densith " + bfpSet.detCM + " Cannot be negative or zero"); return false;}
		
		return true;
	}
	
	//*******************************************************************************

	private void DoRoutine(FanProjectors.BremFanParams bfpSet)
	{
		int width = imp.getWidth();
		int height = imp.getHeight();

		ImageProcessor ip = imp.getProcessor();
		Object image = ip.getPixels();

		if(image instanceof float[])
		{	
			//Run the Fan Beam scan
			//long startTime = System.nanoTime();
			float[] sino = fanPrj.imageToBremsstrahlungFanBeamSinogram(bfpSet,(float [])image,width,height);
			//long endTime = System.nanoTime();
			//long duration = (endTime - startTime);
			//IJ.log("Execution time=" + duration + "nSec");
			
			// append "FanSino" to the image name
            String title;
			String name = imp.getTitle();
            int dotIndex = name.lastIndexOf(".");
            if(dotIndex != -1) title = name.substring(0, dotIndex);
            else title  = name;
 
			ImagePlus sinoImp = IJ.createImage(title + "_FanBremSino" + bfpSet.numAng, (int)(imp.getWidth()*bfpSet.magnification), bfpSet.numAng,1,32);
			
			//Record the scan conditions in the image properties Info
			//They get posted in strange order
			int propCnt = 22 + 4*bfpSet.matlGmPerCC.length;;		
			String[] props = new String[propCnt];
			props[0]="Fan Beam"; 
			props[1]="Bremsstrahlung Sinogram";
			props[2]="Source KV";
			props[3]=Double.toString(bfpSet.kv);
			props[4]="Source mA";
			props[5]=Double.toString(bfpSet.ma);
			props[6]="Source Target";
			props[7]=bfpSet.target;
			props[8]="Filter";
			props[9]=bfpSet.filter;
			props[10]="Filter(cm)";
			props[11]=Double.toString(bfpSet.filterCM);			
			props[12]="Source To Detector";
			props[13]=Double.toString(bfpSet.srcToDetCM);			
			props[14]="Magnification";
			props[15]=Double.toString(bfpSet.magnification);			
			props[16]="Detector";
			props[17]=bfpSet.detFormula;
			props[18]="Detector (cm)";
			props[19]=Double.toString(bfpSet.detCM);
			props[20]="Detector(gm/cc)";
			props[21]=Double.toString(bfpSet.detGmPerCC);
			sinoImp.setProperties(props);
			/*
			The Material tag list can be quite long
			Leave it to the user to keep track of which tags were used.
			for(int i=22, k=0;i<propCnt;i+=4,k++)
			{
				props[i]=("Matl " +(k+1));
				props[i+1]= bfpSet.matlFormula[k];
				props[i+2]= "Matl " + (k +1) + " gmPerCC";
				props[i+3]= Double.toString(bfpSet.matlGmPerCC[k]);
			}
			sinoImp.setProperties(props);
			*/
			
			//put the sinogram into the image
			ImageProcessor sinoIP = sinoImp.getProcessor();
			sinoIP.setPixels(sino);
			
			// Set the sinogram X,Y units
			//The pixel values are in per pixel units
			Calibration  imgCal = imp.getCalibration();		
			String unit = imgCal.getUnit();	// bark if not "cm" ?
			double pixSize = imgCal.getX(1); //cm per pixel
			Calibration sinoCal = sinoImp.getCalibration();
            sinoCal.setXUnit(unit);
            sinoCal.setYUnit("Deg");
            sinoCal.pixelWidth = pixSize;
            sinoCal.pixelHeight = 360.0/bfpSet.numAng;
            
			sinoImp.show();
			
			if(scale16)
			{
				IJ.run(sinoImp, "Multiply...", "value=6000");
				ImageConverter.setDoScaling(false);
				IJ.run(sinoImp, "16-bit", "");
			}					
			IJ.run(sinoImp, "Enhance Contrast", "saturated=0.35");
			
		}
	}
	
	//*******************************************************************************
	//DIALOG SUPPORT FUNCTIONS*******************************************************
	//*******************************************************************************

	private void GetSelections(GenericDialog gd)
	{
		//X-ray Source
		bfpSet.target = gd.getNextChoice();
		bfpSet.kv = gd.getNextNumber();
		bfpSet.ma = gd.getNextNumber();
		bfpSet.nBins=(int) gd.getNextNumber();
		bfpSet.minKV = gd.getNextNumber();

		//Filter
		bfpSet.filter = gd.getNextChoice();
		bfpSet.filterCM = gd.getNextNumber();
		bfpSet.filterGmPerCC = mmc.getAtomGmPerCC(bfpSet.filter);
		
		//Sample
		bfpSet.pixSizeCM = imp.getCalibration().pixelWidth;

		//CT
		bfpSet.numAng = (int)gd.getNextNumber();
		bfpSet.srcToDetCM = (float)gd.getNextNumber();
		bfpSet.magnification = (float)gd.getNextNumber();

		//Detector
		bfpSet.detFormula = gd.getNextString();
		bfpSet.detCM = gd.getNextNumber();
		bfpSet.detGmPerCC = gd.getNextNumber();				
		scale16 = gd.getNextBoolean();
		padImage = gd.getNextBoolean();
	}

	//*******************************************************************************
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent theEvent)
	{

/*		for(int i=0;i< gd.getComponentCount();i++)
		{
			IJ.log("Component[" + i + "]=" + gd.getComponent(i).toString());			
		}
*/
		GetSelections(gd);
		if(theEvent!=null && gd.getTitle()==myDialogTitle)
		{

			if(theEvent.getSource().equals(gd.getComponent(36)))//padImage
			{
				if(padImage)
				{
					int newWH = (int) (Math.ceil(Math.sqrt(oldW*oldW + oldH*oldH)));
					IJ.run("Canvas Size...", "width=" + newWH + " height="+newWH + " position=Center zero");
				}
				else
				{
					IJ.run("Canvas Size...", "width=" + oldW + " height="+oldH + " position=Center zero");				
				}
				
				double srcToSampCM = bfpSet.srcToDetCM/bfpSet.magnification;
				double sampToDetCM = bfpSet.srcToDetCM - srcToSampCM;
				Label sampToDetLbl = (Label) gd.getComponent(26);
				sampToDetLbl.setText("Axis To Detector = " + String.format("%.3f" + " cm", sampToDetCM));

				int detPixCnt= (int)(imp.getWidth()*bfpSet.magnification);
				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));

				Label detPixLbl = (Label) gd.getComponent(27);
				detPixLbl.setText("Detector Pixels = " + detPixCnt);

				TextField nAnglesTxt = (TextField) gd.getComponent(21);
				nAnglesTxt.setText(String.valueOf(numAngles));			
			}
			
			
			if(theEvent.getSource().equals(gd.getComponent(25)) ||
					theEvent.getSource().equals(gd.getComponent(23)))//magnification or srcToDet
			{
				double srcToSampCM = bfpSet.srcToDetCM/bfpSet.magnification;
				double sampToDetCM = bfpSet.srcToDetCM - srcToSampCM;
				Label sampToDetLbl = (Label) gd.getComponent(26);
				sampToDetLbl.setText("Axis To Detector = " + String.format("%.3f" + " cm", sampToDetCM));

				int detPixCnt= (int)(imp.getWidth()*bfpSet.magnification);
				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));

				Label detPixLbl = (Label) gd.getComponent(27);
				detPixLbl.setText("Detector Pixels = " + detPixCnt);

				TextField nAnglesTxt = (TextField) gd.getComponent(21);
				nAnglesTxt.setText(String.valueOf(numAngles));			
			}			
		}
		
		//ValidateParams(bfpSet); //can get annoying
		return true;
	}

	//*******************************************************************************

	public void actionPerformed(ActionEvent e)
	{
		//the Edit materials button
		if(e.getSource().equals(gd.getComponent(18)))
		{
			if(bfpSet.matlGmPerCC!=null)
			{				
				GenericDialog md=new GenericDialog("Materials Dialog");
				md.addMessage("Tag           Name           Formula                   GmPerCC");
				
				for(int i=1;i<bfpSet.matlGmPerCC.length;i++)
				{
					md.addNumericField("", bfpSet.matlTag[i]);
					md.addToSameRow();
					md.addStringField("", bfpSet.matlName[i]);
					md.addToSameRow();
					md.addStringField("", bfpSet.matlFormula[i]);
					md.addToSameRow();
					md.addNumericField("", bfpSet.matlGmPerCC[i]);
				}
				md.showDialog();
	
				if(!md.wasCanceled())
				{
					for(int i=1;i<bfpSet.matlGmPerCC.length;i++)
					{
						bfpSet.matlTag[i]=(int)md.getNextNumber();
						bfpSet.matlName[i]=md.getNextString();
						bfpSet.matlFormula[i]=md.getNextString();
						bfpSet.matlGmPerCC[i]=md.getNextNumber();
					}					
				}
			}
		}
		else if(e.getSource().equals(gd.getComponent(17)))
		{	
			//A Materials file is a simple text file containing rows of comma separated material properties, one material per line.
			//Tag: an integer used to identify image pixels belonging to a material
			//Name: the generic name of the material
			//Formula: the formula in the Atom1:Count1:atom2:Count2 format
			//Density: a density in gmPerCC.
			//The first material is assigned to tag=1
			//The second in assigned to tag=2 etc.
			String path = IJ.getFilePath("Load Materials List");
			String matlStr = IJ.openAsString(path);
			
			//Split materials at newline
			matlArr = matlStr.split("\n");
			bfpSet.matlTag = new int[matlArr.length];
			bfpSet.matlName = new String[matlArr.length];
			bfpSet.matlFormula = new String[matlArr.length];
			bfpSet.matlGmPerCC = new double[matlArr.length];
			
			//split bfpSet.formula and bfpSet.gmPerCC at "," 
			//skip label row
			for(int i=1;i<matlArr.length;i++)
			{
				String[] str = matlArr[i].split(",");
				bfpSet.matlTag[i] = Integer.parseInt(str[0]);
				bfpSet.matlName[i] = str[1];
				bfpSet.matlFormula[i] = str[2];
				bfpSet.matlGmPerCC [i] =  Double.parseDouble(str[3]);
			}			
			gd.setOKLabel("OK");			 			
		}
	}
}

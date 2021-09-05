//package Scanner_Sims;

/*
 * This plugin simulates a parallel beam CT scan from 0 to 180 degrees
 * of a segmented 2D image using a conventional x-ray source and a scintillation
 * detector.
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
 * A Ct scan is simulated by rotating and projecting the image to form a simogram.
 * 
 *  The Image:
 *  1. is segmented into N components. The pixel values of each component is assigned a number ID.
 *  2. for each
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
import jhd.Serialize.Serializer;


public class Parallel_Bremsstrahlung_Scan_Lib implements PlugInFilter , DialogListener, ActionListener
{

	String[] targetSymb = {"Ag","Au","Cr","Cu","Mo","Rh","W"};
	String[] filterSymb = {"Ag","Al","Cu","Er","Mo","Nb","Rh","Ta"};
	
	static final String myDialogTitle = "Polychromatic Parallel Beam CTscan";
	static final String mySettingsTitle = "Polychromatic_ParallelBeam_Params";
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
	ParallelProjectors parPrj = new ParallelProjectors();
	//A serializable class containing the simulator's user supplied parameters
	ParallelProjectors.BremParallelParams bppSet = new  ParallelProjectors.BremParallelParams();
	
	//The class used to serialize (save) the users selections
	Serializer ser = new Serializer();
		
	//*******************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_32;
	}

	//*******************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		
		//the original image width and height
		oldW =ip.getWidth();
		oldH =ip.getHeight();
		String unit = imp.getCalibration().getUnit().toUpperCase();
		if(!unit.equals("CM"))
		{
			IJ.error("Pixel units must be in cm (centimeters)");
			return;
		}
		
		if(imp.getWidth() != imp.getHeight())
		{
			IJ.showMessage("Image must be Square. Check the PadImage Box in the next dialog");
		}

		//Read the saved dialog settings
		bppSet = (ParallelProjectors.BremParallelParams)ser.ReadSerializedObject(settingsPath);
		if(bppSet==null)
		{
			bppSet = GetDialogDefaultSettings();
		}

		if(DoDialog())
		{
			if(ValidateParams(bppSet))
			{
				DoRoutine(bppSet);
				ser.SaveObjectAsSerialized(bppSet, settingsPath);
			}
		}
	}
	
	//*******************************************************************************

	private ParallelProjectors.BremParallelParams GetDialogDefaultSettings()
	{
		ParallelProjectors.BremParallelParams dlogSet = new ParallelProjectors.BremParallelParams();
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
		dlogSet.numAng=(int)(0.5*Math.PI*imp.getWidth());
		padImage=false;

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
		dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
		int detPixCnt= imp.getWidth();


		gd.addDialogListener(this);
		//X-ray Source
		gd.setInsets(10,0,0);
		gd.addMessage("X-ray Source________________",myFont,Color.BLACK);
		gd.addChoice("Target",targetSymb,"W");
		gd.addNumericField("KV", bppSet.kv);
		gd.addNumericField("mA", bppSet.ma);
		gd.addNumericField("KeV Bins", bppSet.nBins);
		gd.addNumericField("Min KeV", bppSet.minKV);
		
		//Filter
		gd.setInsets(10,0,0);
		gd.addMessage("Source Filter________________",myFont,Color.BLACK);
		gd.addChoice("Material",filterSymb,bppSet.filter);
		gd.addNumericField("Thickness(cm)", bppSet.filterCM);
		
		//Materials
		gd.setInsets(10,0,0);
		gd.addMessage("Material Tags________________",myFont,Color.BLACK);
		gd.setInsets(10,50,0);
		gd.addButton("Load Materials File", this);
		gd.setInsets(10,50,0);
		gd.addButton("View/Edit Materials", this);
						
		//CT
		gd.setInsets(10,0,0);
		gd.addMessage("180 degree Scan______________",myFont,Color.BLACK);
		//int numAngles = (int) (imp.getWidth()*1.570796327);
		gd.addNumericField("Suggested View Angles", bppSet.numAng);
		gd.setInsets(0,0,0);
		gd.addMessage("Detector Pixel Count = " + detPixCnt);

		//Detector
		gd.setInsets(10,0,0);
		gd.addMessage("Detector___________________",myFont,Color.BLACK);
		gd.addStringField("Formula", bppSet.detFormula);
		gd.addNumericField("Thickness(cm)", bppSet.detCM);
		gd.addNumericField("Density(gm/cc)", bppSet.detGmPerCC);
		gd.addCheckbox("Scale to 16-bit proj", false);
		gd.addCheckbox("Pad Image", false);
     	 		
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

	private boolean ValidateParams(ParallelProjectors.BremParallelParams bppSet)
	{
		//Test the formulas
		ArrayList<AtomData> formula;

		//Pre-screen the formulas for correctness
		formula = mmc.createFormulaList(bppSet.target);
		if(formula==null) {IJ.error(bppSet.target + " Is not a valid target material"); return false;}
		formula = mmc.createFormulaList(bppSet.filter);
		if(formula==null) {IJ.error(bppSet.filter + " Is not a valid filter material"); return false;}
		
		if(bppSet.matlFormula==null){IJ.error("Missing Formulas"); return false;}
		if(bppSet.matlGmPerCC==null){IJ.error("Missing Densities"); return false;}
		
		for(int i=1;i< bppSet.matlFormula.length;i++)
		{
			if(bppSet.matlGmPerCC[i] <= 0){IJ.error("Material 1 Density " + bppSet.matlGmPerCC[i] + " Cannot be negative"); return false;}
			if(bppSet.matlFormula[i] == null){IJ.error("Missing Formula at item " + i); return false;}
			formula = mmc.createFormulaList(bppSet.matlFormula[i]);
			if(formula==null) {IJ.error(bppSet.matlFormula[i] + " Is not a valid  material"); return false;}			
		}
		
		formula = mmc.createFormulaList(bppSet.detFormula);
		if(formula==null) {IJ.error(bppSet.detFormula + " Is not a valid detector material"); return false;}
		
		//Test the numbers
		if(bppSet.kv < bppSet.minKV){IJ.error("Source KV " + bppSet.kv + " Must be greater than " + bppSet.minKV + "KV"); return false;}
		if(bppSet.kv <=0){IJ.error("Source KV " + bppSet.kv + " Must be greater than 0 KV"); return false;}
		if(bppSet.ma <=0){IJ.error("Source mA " + bppSet.ma + " Must be greater than 0 mA"); return false;}
		if(bppSet.nBins <=0){IJ.error("Bin Count " + bppSet.nBins + " Must be greater than 0"); return false;}
		if(bppSet.minKV > bppSet.kv){IJ.error("Source minMV " + bppSet.minKV + " Must be less than " + bppSet.kv + "KV"); return false;}
		
		if(bppSet.filterCM < 0){IJ.error("Filter Thickness " + bppSet.filterCM + " Cannot be negative"); return false;}
		if(bppSet.filterGmPerCC <= 0){IJ.error("Filter Density " + bppSet.filterGmPerCC + " Cannot be negative"); return false;}
		
		if(bppSet.numAng < 1){IJ.error("Number of angles " + bppSet.numAng + " Cannot be negative or zero"); return false;}
		if(bppSet.detCM <= 0){IJ.error("Detector Thickness " + bppSet.detCM + " Cannot be negative"); return false;}
		if(bppSet.detGmPerCC <= 0){IJ.error("Detector Densith " + bppSet.detCM + " Cannot be negative or zero"); return false;}
		
		return true;
	}
	
//*******************************************************************************

	private void DoRoutine(ParallelProjectors.BremParallelParams bppSet)
	{
		int width = imp.getWidth();
		int height = imp.getHeight();

		ImageProcessor ip = imp.getProcessor();
		Object image = ip.getPixels();

		if(image instanceof float[])
		{	
			//Call the MuMassCalculator Projector library function
			//long startTime = System.nanoTime();
			float[] sino = parPrj.imageToBremsstrahlungParallelSinogram(bppSet, (float [])image, width, height);
			//long endTime = System.nanoTime();
			//long duration = (endTime - startTime);
			//IJ.log("Execution time=" + duration + "nSec");
			
			// append "ParBremSino" and the angle count to the image name
            String title;
			String name = imp.getTitle();
            int dotIndex = name.lastIndexOf(".");
            if(dotIndex != -1) title = name.substring(0, dotIndex);
            else title  = name;
            
			ImagePlus sinoImp = IJ.createImage(title + "_ParBremSino" + bppSet.numAng, imp.getWidth(), bppSet.numAng,1,32);
			
			//Record the scan conditions in the image properties Info
			//They get posted in strange order
			int propCnt = 18 + 4*bppSet.matlGmPerCC.length;;		
			String[] props = new String[propCnt];
			props[0]="Parallel Beam"; 
			props[1]="Bremsstrahlung Sinogram";
			props[2]="Source KV";
			props[3]=Double.toString(bppSet.kv);
			props[4]="Source mA";
			props[5]=Double.toString(bppSet.ma);
			props[6]="Source Target";
			props[7]=bppSet.target;
			props[8]="Filter";
			props[9]=bppSet.filter;
			props[10]="Filter(cm)";
			props[11]=Double.toString(bppSet.filterCM);
			props[12]="Detector";
			props[13]=bppSet.detFormula;
			props[14]="Detector (cm)";
			props[15]=Double.toString(bppSet.detCM);
			props[16]="Detector(gm/cc)";
			props[17]=Double.toString(bppSet.detGmPerCC);
			sinoImp.setProperties(props);
			/*
			The Material tag list can be quite long
			Leave it to the user to keep track of which tags were used.
			for(int i=22, k=0;i<propCnt;i+=4,k++)
			{
				props[i]=("Matl " +(k+1));
				props[i+1]= bppSet.matlFormula[k];
				props[i+2]= "Matl " + (k +1) + " gmPerCC";
				props[i+3]= Double.toString(bppSet.matlGmPerCC[k]);
			}
			sinoImp.setProperties(props);
			*/
			sinoImp.setProperties(props);
			
			//put the sinogram into the image
			ImageProcessor sinoIP = sinoImp.getProcessor();
			sinoIP.setPixels(sino);
			
			// Set the sinogram X,Y units
			//The sinogram pixel values are in per pixel units
			Calibration  imgCal = imp.getCalibration();		
			String unit = imgCal.getUnit();	// bark if not "cm" ?
			double pixSize = imgCal.getX(1); //cm per pixel
			Calibration sinoCal = sinoImp.getCalibration();
            sinoCal.setXUnit(unit);
            sinoCal.setYUnit("Deg");
            sinoCal.pixelWidth = pixSize;
            sinoCal.pixelHeight = 180.0/bppSet.numAng;
            
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

	private void GetSelections(GenericDialog gd)
	{
		//X-ray Source
		bppSet.target = gd.getNextChoice();
		bppSet.kv = gd.getNextNumber();
		bppSet.ma = gd.getNextNumber();
		bppSet.nBins=(int) gd.getNextNumber();
		bppSet.minKV = gd.getNextNumber();

		//Filter
		bppSet.filter = gd.getNextChoice();
		bppSet.filterCM = gd.getNextNumber();
		bppSet.filterGmPerCC = mmc.getAtomGmPerCC(bppSet.filter);
		
		//Sample
		bppSet.pixSizeCM = imp.getCalibration().pixelWidth;
		
		//CT
		bppSet.numAng = (int)gd.getNextNumber();

		//Detector
		bppSet.detFormula = gd.getNextString();
		bppSet.detCM = gd.getNextNumber();
		bppSet.detGmPerCC = gd.getNextNumber();				
		scale16 = gd.getNextBoolean();
		padImage = gd.getNextBoolean();
	}

	//*******************************************************************************
	
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent theEvent)
	{
/*				for(int i=0;i< gd.getComponentCount();i++)
		{
			IJ.log("Component[" + i + "]=" + gd.getComponent(i).toString());			
		}
*/
		GetSelections(gd);
		if(theEvent!=null && gd.getTitle()==myDialogTitle)
		{

			if(theEvent.getSource().equals(gd.getComponent(31)))//padImage
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
				
				//imp.updateAndDraw();

				int detPixCnt= imp.getWidth();
				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));

				Label detPixLbl = (Label) gd.getComponent(22);
				detPixLbl.setText("Detector Pixels = " + detPixCnt);

				TextField nAnglesTxt = (TextField) gd.getComponent(21);
				nAnglesTxt.setText(String.valueOf(numAngles));			
			}	
		}
		//ValidateParams(bppSet); //can get annoying
		return true;
	}

	//*******************************************************************************

	public void actionPerformed(ActionEvent theEvent)
	{
		//the Edit materials button
		if(theEvent.getSource().equals(gd.getComponent(18)))
		{
			if(bppSet.matlGmPerCC!=null)
			{				
				GenericDialog md=new GenericDialog("Materials Dialog");
				md.addMessage("Tag           Name           Formula                   GmPerCC");
				
				for(int i=1;i<bppSet.matlGmPerCC.length;i++)
				{
					md.addNumericField("", bppSet.matlTag[i]);
					md.addToSameRow();
					md.addStringField("", bppSet.matlName[i]);
					md.addToSameRow();
					md.addStringField("", bppSet.matlFormula[i]);
					md.addToSameRow();
					md.addNumericField("", bppSet.matlGmPerCC[i]);
				}
				md.showDialog();
	
				if(!md.wasCanceled())
				{
					for(int i=1;i<bppSet.matlGmPerCC.length;i++)
					{
						bppSet.matlTag[i]=(int)md.getNextNumber();
						bppSet.matlName[i]=md.getNextString();
						bppSet.matlFormula[i]=md.getNextString();
						bppSet.matlGmPerCC[i]=md.getNextNumber();
					}					
				}
			}
		}
		else if(theEvent.getSource().equals(gd.getComponent(17)))
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
			bppSet.matlTag = new int[matlArr.length];
			bppSet.matlName = new String[matlArr.length];
			bppSet.matlFormula = new String[matlArr.length];
			bppSet.matlGmPerCC = new double[matlArr.length];
			
			//split bppSet.formula and bppSet.gmPerCC at "," 
			//skip label row
			for(int i=1;i<matlArr.length;i++)
			{
				String[] str = matlArr[i].split(",");
				bppSet.matlTag[i] = Integer.parseInt(str[0]);
				bppSet.matlName[i] = str[1];
				bppSet.matlFormula[i] = str[2];
				bppSet.matlGmPerCC [i] =  Double.parseDouble(str[3]);
			}			
			gd.setOKLabel("OK");			 			
		}
	}
}

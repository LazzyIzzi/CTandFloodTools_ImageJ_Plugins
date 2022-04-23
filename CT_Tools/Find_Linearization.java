package CT_Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.Plot;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.measure.ResultsTable;

import java.awt.event.*;
import java.util.Vector;
import java.awt.*;

//import java.awt.AWTEvent;
//import java.awt.Color;
//import java.awt.Font;

import jhd.MuMassCalculator.*;
import jhd.Projection.*;
import tagTools.common.*;

/* 	Estimate "polynomial linearization" beam hardening coefficients from an uncorrected reconstructed slice.
A copy of the slice is thresholded into homogeneous components using
user-supplied gray level bounds and the expected linear
attenuation coefficients.

The expected values can be obtained beforehand
by calculation using energy, composition and density of the sample
or by measuring the average linear attenuation for the component
in the uncorrected reconstructed slice.

The the method works best if the sample is relatively homogeneous, i.e. the sample can
contain many components but the path fractions of each component are roughly the same
for all rays passing through the sample.


JHD 1/18/22

May need to add an option to smooth the model image
*/

public class Find_Linearization implements PlugInFilter, DialogListener, ActionListener
{
	MuMassCalculator mmc = new MuMassCalculator();
	ParallelProjectors prj= new ParallelProjectors();
	
	GenericDialog gd;
	Calibration cal;
	CurveFitter crvfit;
	ImagePlus dataImp,modelImp;
	ImageProcessor ip;
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	
	MatlListTools2.TagSet myTagSet;	
	MatlListTools2 mlt = new MatlListTools2();
	String[] matlNames;

	int		matlIndex;	// the position of the material in the list
	float	low,high;
	float[] tauLUT;
	double	keV;
	final int nViews = 18; // the number of sinogram projection to evaluate

	//*****************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.dataImp = imp;
		return DOES_32;
	}

	//*****************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		this.ip = ip;
		
		if(ip.getWidth()!= ip.getHeight())
		{
			IJ.error("Image Must be square.");			
			return;
		}
		if(IJ.versionLessThan("1.41k")) return;
		Calibration cal = dataImp.getCalibration();
		if(!cal.getUnit().equals("cm"))
		{
			IJ.error("Pixel units must be cm");
			return;
		}

		DoDialog();

		//IJ.run("Tile");
	}

	//*****************************************************************

	private void DoDialog()
	{
		String dir = IJ.getDirectory("plugins");
		String path = dir + "DialogData\\DefaultMaterials.csv";
		myTagSet = mlt.loadTagFile(path);
		
		//Get names array from TagSet
		matlNames = new String[myTagSet.tagData.size()];
		int i=0;
		for(MatlListTools2.TagData td : myTagSet.tagData)
		{
			matlNames[i]= td.matlName;
			i++;
		}
		
		
		if(myTagSet!=null)
		{
			
			modelImp = IJ.createImage("TagImage", dataImp.getWidth(), dataImp.getHeight(), 1, dataImp.getBitDepth());
			modelImp.setCalibration(cal);
			modelImp.show();
			//put the tag image to right of CT slice
			int left = dataImp.getWindow().getX();
			int top = dataImp.getWindow().getY();
			modelImp.getWindow().setLocation(left+dataImp.getWidth() + 20, top);
			
			gd = new GenericDialog("Find Beam Hardening Corrections");
			//Materials
			gd.setInsets(10,0,0);
			gd.addMessage("This Plugin requires a square image with\n"
					+ "isotropic pixel sizes in cm units and pixel \n"
					+ "values in cm-1.\n \n"
					+ "Step 1: For each material in the image\n"
					+ "Select a material from the menu.\n"
					+ "Move the sliders to select thresholds.\n"
					+ "Click \"Add \" button.",myFont,Color.BLACK);
	
			gd.addChoice("Material: ",matlNames,matlNames[1]);
			
			ImageStatistics stats = dataImp.getStatistics();
			gd.addSlider("Lower", stats.min, stats.max, stats.min);
			gd.addSlider("Upper", stats.min, stats.max, stats.max);
			
			gd.setInsets(10,50,0);
			gd.addButton("Add Material to Tag Image", this);
			gd.setInsets(10,50,0);
			gd.addMessage("Step 2:\n"
					+ "Enter a estimated X-ray energy.\n"
					+ "Click \"OK\" when done.",myFont,Color.BLACK);
			gd.addNumericField("Est. keV", 100);
			gd.setInsets(10,50,0);
			gd.addMessage("Click OK\n"
					+ "In the plot window, use \"Data->Add Fit\"\n"
					+ "to select the best fit.\n"
					+ "Use Apply Correction plugin to apply\n"
					+ "the correction to the CT slice's original sinogram",myFont,Color.BLACK);
			gd.addDialogListener(this);
			gd.addHelp("https://lazzyizzi.github.io/Linearization.html");
			gd.showDialog();

			if(gd.wasOKed())
			{
				DoRoutine();
				ip.resetThreshold();
				dataImp.updateAndDraw();
				modelImp.changes=true;
				//modelImp.close();
				//ip.resetThreshold();
				//imp.updateAndDraw();				
			}
			if(gd.wasCanceled())
			{
				modelImp.close();
				return;
			}
		}
		else return;		
	}

	//*****************************************************************

	private void DoRoutine()
	{
		//Convert TagImg to tau
		float[] tagPix = (float[])modelImp.getProcessor().getPixels();
		mlt.tagsToLinearAttn(tagPix, myTagSet, keV);
		
		int width = dataImp.getWidth();
		//int height = dataImp.getHeight();

		
		double pixSize = dataImp.getCalibration().pixelWidth;
		float[] dataImg = (float[]) dataImp.getProcessor().getPixels();
		float[] modelImg = (float[]) modelImp.getProcessor().getPixels();
		
		//Single projection comparison. Column average the images
//		double[] dataProj = new double[width];
//		double[] modelProj = new double[modelImp.getWidth()];
//		for(int j = 0;j<height;j++)
//		{
//			for(int i = 0;i< width;i++)
//			{
//				dataProj[i] += (double) dataImg[i + j*width]*pixSize;
//				modelProj[i] += (double) modelImg[i + j*width]*pixSize;
//			}
//		}		
//		Plot tauPlot = new Plot("Model vs Data",  "Data Proj","Model Proj");
//		tauPlot.add("line", dataProj, modelProj );
//		tauPlot.show();
		//End Single projection comparison
		
		//Sinogram comparison.
		ParallelProjectors parPrj = new ParallelProjectors();
		float[] dataSino = parPrj.imageToParallelSinogram(dataImg, width, width, nViews);
		float[] modelSino = parPrj.imageToParallelSinogram(modelImg, width, width, nViews);
		
		double[] dataProj = new double[width*nViews];
		double[] modelProj = new double[width*nViews];
		for(int i = 0;i< width*nViews;i++)
			{
				dataProj[i] += (double) dataSino[i]*pixSize;
				modelProj[i] += (double) modelSino[i]*pixSize;
			}
//			//The sinograms are already projections
		
		Plot tauPlot = new Plot("Model vs Data for " + dataImp.getTitle() + " at " + keV +"keV",  "Data Proj","Model Proj");
		//Plot tauPlot = new Plot("Model vs Data",  "Data Proj","Model Proj");
		tauPlot.setColor(Color.RED);
		tauPlot.add("dot", dataProj, modelProj );
		tauPlot.show();

		String resultTitle = "Fit Params for " + dataImp.getTitle() + " at " + keV +"keV";
		//tauPlot.setFrozen(true);
		ResultsTable fitRT = new ResultsTable();
		fitRT.setPrecision(5);
		//fitRT.show(resultTitle);

		String[] hdr = {"A","B","C","D","E","F","G","H"};

		double[] fitParams;
		int i;
		
		fitRT.incrementCounter();
		crvfit = new CurveFitter(dataProj,modelProj);
		crvfit.doFit(CurveFitter.POLY6);
		fitParams = crvfit.getParams();
		fitRT.addValue("Fit", CurveFitter.fitList[CurveFitter.POLY6]);
		for(i=0;i< fitParams.length-1;i++)
		{
			fitRT.addValue(hdr[i], fitParams[i]);
		}
		fitRT.addValue("R^2", crvfit.getRSquared());
				
		fitRT.incrementCounter();
		crvfit = new CurveFitter(dataProj,modelProj);
		crvfit.doFit(CurveFitter.POLY5);
		fitParams = crvfit.getParams();
		fitRT.addValue("Fit", CurveFitter.fitList[CurveFitter.POLY5]);
		for(i=0;i< fitParams.length-1;i++)
		{
			fitRT.addValue(hdr[i], fitParams[i]);
		}
		fitRT.addValue("R^2", crvfit.getRSquared());
		
		fitRT.incrementCounter();
		crvfit = new CurveFitter(dataProj,modelProj);
		crvfit.doFit(CurveFitter.POLY4);
		fitParams = crvfit.getParams();
		fitRT.addValue("Fit", CurveFitter.fitList[CurveFitter.POLY4]);
		for(i=0;i< fitParams.length-1;i++)
		{
			fitRT.addValue(hdr[i], fitParams[i]);
		}
		fitRT.addValue("R^2", crvfit.getRSquared());

		fitRT.incrementCounter();
		crvfit = new CurveFitter(dataProj,modelProj);
		crvfit.doFit(CurveFitter.POLY3);
		fitParams = crvfit.getParams();
		fitRT.addValue("Fit", CurveFitter.fitList[CurveFitter.POLY3]);
		for(i=0;i< fitParams.length-1;i++)
		{
			fitRT.addValue(hdr[i], fitParams[i]);
		}
		fitRT.addValue("R^2", crvfit.getRSquared());
		
		fitRT.incrementCounter();
		crvfit = new CurveFitter(dataProj,modelProj);
		crvfit.doFit(CurveFitter.POLY2);
		fitParams = crvfit.getParams();
		fitRT.addValue("Fit", CurveFitter.fitList[CurveFitter.POLY2]);
		for(i=0;i< fitParams.length-1;i++)
		{
			fitRT.addValue(hdr[i], fitParams[i]);
		}
		fitRT.addValue("R^2", crvfit.getRSquared());

		fitRT.incrementCounter();
		crvfit = new CurveFitter(dataProj,modelProj);
		crvfit.doFit(CurveFitter.STRAIGHT_LINE);
		fitParams = crvfit.getParams();	
		fitRT.addValue("Fit", CurveFitter.fitList[CurveFitter.STRAIGHT_LINE]);
		for(i=0;i< fitParams.length-1;i++)
		{
			fitRT.addValue(hdr[i], fitParams[i]);
		}
		fitRT.addValue("R^2", crvfit.getRSquared());
		
		fitRT.show(resultTitle);
	}

	//*****************************************************************
	
	@SuppressWarnings("unchecked")
	private void GetSelections()
	{
		String str;

		Vector<Choice> choices = gd.getChoices();
		matlIndex = choices.get(0).getSelectedIndex();
		
		Vector<TextField> numbers = gd.getNumericFields();		
		str = numbers.get(0).getText();
		if(isNumeric(str)) 	low =  Float.valueOf(str);
		str = numbers.get(1).getText();
		if(isNumeric(str)) 	high =  Float.valueOf(str);
		str = numbers.get(2).getText();
		if(isNumeric(str)) 	keV =  Float.valueOf(str);
	}

	public static boolean isNumeric(String str)
	{ 
		try
		{  
			Double.parseDouble(str);  
			return true;
		}
		catch(NumberFormatException e)
		{  
			return false;  
		}  
	}

	//*****************************************************************
	
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
	   	GetSelections();
		ip.setThreshold(low, high, ImageProcessor.RED_LUT);
		dataImp.updateAndDraw();
		return true;
	}

	//*****************************************************************

	@Override
	public void actionPerformed(ActionEvent e)
	{
		Button btn = (Button)e.getSource();
		String btnLabel = btn.getLabel();
		switch(btnLabel)
		{
		case "Add Material to Tag Image":
			GetSelections();
			if(matlIndex>0)
			{
				float[] pix = (float[])ip.getPixels();
				float[] tagPix = (float[])modelImp.getProcessor().getPixels();
				for(int i=0;i<pix.length;i++)
				{
					if(pix[i]<high && pix[i] > low)
					{
						tagPix[i] = myTagSet.tagData.get(matlIndex).matlTag;
					}
				}
				ip.resetThreshold();
				dataImp.updateAndDraw();
				modelImp.getProcessor().setMinAndMax(modelImp.getStatistics().min, modelImp.getStatistics().max);
				IJ.run(modelImp, "3-3-2 RGB", "");
				modelImp.updateAndDraw();
			}
			break;
		}
	}

}

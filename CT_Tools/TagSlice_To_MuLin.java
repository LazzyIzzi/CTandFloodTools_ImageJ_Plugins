package CT_Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import jhd.MuMassCalculator.MuMassCalculator;
import tagTools.common.*;

import java.util.Vector;
import java.awt.*;

/** A plugin for converting tag images to linear attenuation*/
public class TagSlice_To_MuLin implements PlugInFilter
{
	MuMassCalculator mmc = new MuMassCalculator();
	MatlListTools2 mlt = new MatlListTools2();
	MatlListTools2.TagSet myTagSet;
	String[] matlNames;
	double keV=100;
	
	GenericDialog gd;
	Calibration cal;
	ImagePlus imp;
	ImageProcessor ip;
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	
	//***********************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32;
	}

	//***********************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		this.ip = ip;
		cal = imp.getCalibration();
		if(!cal.getUnit().equals("cm"))
		{
			IJ.error("Pixel units must be cm");
			return;
		}
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
			gd = new GenericDialog("Convert Tags to MuLin");
			gd.addMessage("Select and energy.\nClick \"Convert\"",myFont,Color.BLACK);
			gd.addNumericField("KeV:", keV);
			gd.addHelp("https://lazzyizzi.github.io/");
			//gd.setInsets(10,50,0);
			gd.showDialog();

			if(gd.wasCanceled())
			{
				return;
			}
			else
			{
				GetSelections();
				ImagePlus tauImp = imp.duplicate();
				tauImp.setTitle("MuMass at " + keV + "KeV");

				float[] tauPix = (float[])tauImp.getProcessor().getPixels();
				if(mlt.tagsToLinearAttn(tauPix, myTagSet, keV))
				{
					tauImp.getProcessor().setMinAndMax(tauImp.getStatistics().min, tauImp.getStatistics().max);
					tauImp.show();
					tauImp.updateAndDraw();
					tauImp.show();
					//IJ.run("Tile");
				}
			}
		}
	}
	
	//***********************************************************************************

	@SuppressWarnings("unchecked")
	private void GetSelections()
	{
		Vector<TextField> numbers = gd.getNumericFields();
		String str= numbers.get(0).getText();
		if(isNumeric(str))	keV =  Float.valueOf(str);
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

}

package CT_Tools;

import java.util.Properties;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;

public class Show_Sinogram_Properties implements PlugIn
{
	final static String[] tags = {"Geometry","Source","Source KV","Source mA","Source Target","Min keV","Bins",
			"Filter","Filter(cm)","Source To Detector","Magnification","Detector","Detector(cm)","Detector(gm/cc)"};

	ImagePlus imp;
	@Override
	public void run(String arg)
	{
		imp = IJ.getImage();
		ResultsTable rt = ResultsTable.getResultsTable("Sinogram Properties");
		if(rt==null) rt = new ResultsTable();
		
		rt.setPrecision(3);
		rt.incrementCounter();
		rt.addValue("Name", imp.getTitle());
		Properties props = imp.getImageProperties();
		for(int i=0;i< tags.length;i++)
		{
			String val = props.getProperty(tags[i]);
			if(val!=null)
			{
				if(isNumeric(val))
				{
					rt.addValue(tags[i], Double.valueOf(val));
				}
				else
				{
					rt.addValue(tags[i],val);
				}
			}
			else
			{
				rt.addValue(tags[i],"N/A");
			}
		}
		rt.show("Sinogram Properties");
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

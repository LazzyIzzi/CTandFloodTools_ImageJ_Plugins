package CT_Tools;

import ij.IJ;
import ij.plugin.PlugIn;
import tagTools.common.*;

public class Materials_Editor implements PlugIn {
	MatlListTools2 mlt = new MatlListTools2();
	MatlListTools2.TagSet myTags;

	@Override
	public void run(String arg)
	{
		String path = IJ.getFilePath(" View Materials file");
		if(path!=null)
		{
			myTags = mlt.loadTagFile(path);
			if(myTags!=null)
			{
				int last = path.lastIndexOf('\\')+1;
				String fileName =path.substring(last);
				mlt.editTagFile(fileName, myTags);
			}
		}
	}
}


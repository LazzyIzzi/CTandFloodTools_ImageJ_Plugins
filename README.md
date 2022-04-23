# CTandFloodTools_ImageJ_Plugins
ImageJ plugins implementing MuMassCalculator and DistanceMapLib methods.
<a href="https://github.com/LazzyIzzi/MuMassCalculator" target="_blank">MuMassCalculator</a>
These ImageJ plugins call the methods in <a href="https://github.com/LazzyIzzi/MuMassCalculator" target="_blank">MuMassCalculator</a> and <a href="https://github.com/LazzyIzzi/DistanceMapLib" target="_blank">DistanceMapLib</a> libraries to perform their work.

See <a href="https://lazzyizzi.github.io/" target="_blank">lazzyizzi.github.io</a> for additional information about the libraties and these plugins.


**Why write plugins this way?**
The data and methods supplied by the libraries are well defined.  In my opinion it makes a lot of sense to "encapsulate" them in a java library independent of ImageJ.  The plugins in this repo provide the user interface to the library.  The library is easily portable, the plugins are not.  my $.02 LZIZ

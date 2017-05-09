
Alfresco Version by Name
========================

When uploading files having the same to a folder in Alfresco, the platform renames that new uploaded file (including "-1" and so on counters) and creates a new file.

```
1: Folder > Test.pdf
2: Upload Test.pdf to Folder
3: Folder > Test.pdf, Test-1.pdf
4: Upload Test.pdf to Folder
5: Folder > Test.pdf, Test-1.pdf, Test-2.pdf
...
```

This addon provides a behaviour to auto-versioning the existing file with the content of the new one.

```
1: Folder > Test.pdf
2: Upload Test.pdf to Folder
3: Folder > Test.pdf [1.1]
4: Upload Test.pdf to Folder
5: Folder > Test.pdf [1.2]
...
```
Video demo available

[![Demo video](https://img.youtube.com/vi/27gjzEPOomo/0.jpg)](https://www.youtube.com/watch?v=27gjzEPOomo)

***Extra material***

How to develop this addon (in Spanish)

[![Developing in Alfresco](https://img.youtube.com/vi/ekPyWLFmip0/0.jpg)](https://www.youtube.com/watch?v=ekPyWLFmip0)


**License**
The plugin is licensed under the [LGPL v3.0](http://www.gnu.org/licenses/lgpl-3.0.html). 

**State**
Current addon release is 1.3.0

**Compatibility**
The current version has been developed using Alfresco 5.0.d and Alfresco SDK 2.1.1, but also runs in Alfresco 5.1.x

From 1.3.0 is also compatible with Alfresco 4.2.x

***No original Alfresco resources have been overwritten***


Downloading the ready-to-deploy-plugin
--------------------------------------
The binary distribution is made of one amp file to be deployed in Alfresco as a repo module:

* [repo AMP](https://github.com/keensoft/alfresco-version-by-name/releases/download/1.3.0/version-by-name-repo.amp)

You can install it by using standard [Alfresco deployment tools](http://docs.alfresco.com/community/tasks/dev-extensions-tutorials-simple-module-install-amp.html) in `alfresco.war`


Building the artifacts
----------------------
You can build the artifacts from source code using maven
```$ mvn clean package```


Notes
-----
This addon does not allow the use of the feature with WebDAV.


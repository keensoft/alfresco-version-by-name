package es.keensoft.alfresco.behaviour;

import java.net.URLEncoder;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.activities.ActivityType;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.activities.ActivityService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONStringer;
import org.json.JSONWriter;

public class AutoVersionByNameBehaviour implements NodeServicePolicies.OnCreateNodePolicy {
	
    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private ContentService contentService;
    private ActivityService activityService;
    private SiteService siteService;
    private FileFolderService fileFolderService;

	public void init() {
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME, ContentModel.TYPE_CONTENT,
                new JavaBehaviour(this, "onCreateNode", Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
	}
	
	@Override
	public void onCreateNode(ChildAssociationRef childAssocRef) {
		
        final NodeRef uploadedNodeRef = childAssocRef.getChildRef();
        
        if (nodeService.exists(uploadedNodeRef) && isContentDoc(uploadedNodeRef)) {
        	
        	NodeRef previouslyExistentDoc = existedPreviousDocument(uploadedNodeRef);
        	
        	if (previouslyExistentDoc != null) {
        		
				ContentReader reader = contentService.getReader(uploadedNodeRef, ContentModel.PROP_CONTENT);
				ContentWriter writer = contentService.getWriter(previouslyExistentDoc, ContentModel.PROP_CONTENT, true);
				
				writer.putContent(reader);
				
			    // Solving issue #2: nodes marked as hidden are not included in postActivity processor 
				nodeService.addAspect(uploadedNodeRef, ContentModel.ASPECT_HIDDEN, null);
				postActivityUpdated(previouslyExistentDoc);
				nodeService.deleteNode(uploadedNodeRef);
        		
        	}
        	
        }
        
	}
	
	// As we are skipping duplicated file uploading, we need to post a new UPDATE on previously existing document
	private void postActivityUpdated(NodeRef nodeRef) {
		
		SiteInfo siteInfo = siteService.getSite(nodeRef);
		String jsonActivityData = "";
		try {
            JSONWriter jsonWriter = new JSONStringer().object();
            jsonWriter.key("title").value(nodeService.getProperty(nodeRef, ContentModel.PROP_NAME).toString());
            jsonWriter.key("nodeRef").value(nodeRef.toString());
			StringBuilder sb = new StringBuilder("document-details?nodeRef=");
			sb.append(URLEncoder.encode(nodeRef.toString(), "UTF-8"));
			jsonWriter.key("page").value(sb.toString());
            jsonActivityData = jsonWriter.endObject().toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
        activityService.postActivity(
				ActivityType.FILE_UPDATED, 
				(siteInfo == null ? null : siteInfo.getShortName()), 
				(siteInfo == null ? null : SiteService.DOCUMENT_LIBRARY), 
				jsonActivityData);

	}
	
    private boolean isContentDoc(NodeRef nodeRef) {
    	return nodeService.getType(nodeService.getPrimaryParent(nodeRef).getParentRef()).isMatch(ContentModel.TYPE_FOLDER);
    }
    
    // Better performance than using "NodeService.getChildAssocs()" method for high volumes ( > 3,000 children)
	// FTS query, which is also a safe method to retrieve one child, is not synchronous
    private NodeRef existedPreviousDocument(NodeRef currentNodeRef) {
    	
    	String fileName = cleanNumberedSuffixes(nodeService.getProperty(currentNodeRef, ContentModel.PROP_NAME).toString());
    	if (!fileName.equals(nodeService.getProperty(currentNodeRef, ContentModel.PROP_NAME).toString())) {
	    	NodeRef folder = nodeService.getPrimaryParent(currentNodeRef).getParentRef();
	    	return nodeService.getChildByName(folder, ContentModel.ASSOC_CONTAINS, fileName);
    	}
    	return null;
    	
    }
    
    // Alfresco includes "-1" and so on for repeated filenames in the same folder, this method remove this addition from the file name
    public static String cleanNumberedSuffixes(String fileName) {
    	
    	String cleanedFileName = fileName;
    	String baseName = FilenameUtils.getBaseName(fileName);
    	if (baseName.indexOf("-") != -1) {
    		if (isInteger(baseName.substring(baseName.lastIndexOf("-") + 1, baseName.length()))) {
    			return baseName.substring(0, baseName.lastIndexOf("-")) + FilenameUtils.EXTENSION_SEPARATOR_STR + FilenameUtils.getExtension(fileName); 
    		}
    	}
    	return cleanedFileName;
    			
    }
    
    public static boolean isInteger(String s) {
        boolean isValidInteger = false;
        try {
           Integer.parseInt(s);
           isValidInteger = true;
        } catch (NumberFormatException ex) {}
        return isValidInteger;
    }    

	public void setPolicyComponent(PolicyComponent policyComponent) {
		this.policyComponent = policyComponent;
	}


	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}

	public void setActivityService(ActivityService activityService) {
		this.activityService = activityService;
	}

	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	public void setFileFolderService(FileFolderService fileFolderService) {
		this.fileFolderService = fileFolderService;
	}

}

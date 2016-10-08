package es.keensoft.alfresco.behaviour;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.io.FilenameUtils;

public class AutoVersionByNameBehaviour implements NodeServicePolicies.OnCreateNodePolicy {
	
    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private ContentService contentService;

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
				nodeService.deleteNode(uploadedNodeRef);
        		
        	}
        	
        }
        
	}
	
    private boolean isContentDoc(NodeRef nodeRef) {
    	return nodeService.getType(nodeService.getPrimaryParent(nodeRef).getParentRef()).isMatch(ContentModel.TYPE_FOLDER);
    }
    
    private NodeRef existedPreviousDocument(NodeRef currentNodeRef) {
    	
    	String fileName = cleanNumberedSuffixes(nodeService.getProperty(currentNodeRef, ContentModel.PROP_NAME).toString());
    	NodeRef folder = nodeService.getPrimaryParent(currentNodeRef).getParentRef();
    	
    	// FIXME This method could be potentially dangerous when having more than 3,000 childs (!) 
    	for (ChildAssociationRef child : nodeService.getChildAssocs(folder)) {
    		String currentName = nodeService.getProperty(child.getChildRef(), ContentModel.PROP_NAME).toString();
    		if (currentName.equals(fileName) && !(child.getChildRef().getId().equals(currentNodeRef.getId()))) {
    			return child.getChildRef();
    		}
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

}

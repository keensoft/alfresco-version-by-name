package es.keensoft.alfresco.behaviour;

import java.util.concurrent.ThreadPoolExecutor;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.authentication.AuthenticationUtil.RunAsWork;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.repo.transaction.TransactionListener;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.Pair;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.apache.commons.io.FilenameUtils;

public class AutoVersionByNameBehaviour implements NodeServicePolicies.OnCreateNodePolicy {
	
	private static final String KEY_RELATED_NODE = AutoVersionByNameBehaviour.class.getName() + ".relatedNodes";
	
    private PolicyComponent policyComponent;
    private NodeService nodeService;
    private ContentService contentService;
	private TransactionService transactionService;
    private TransactionListener transactionListener;
    private ThreadPoolExecutor threadPoolExecutor;

	public void init() {
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME, ContentModel.TYPE_CONTENT,
                new JavaBehaviour(this, "onCreateNode", Behaviour.NotificationFrequency.TRANSACTION_COMMIT));
        transactionListener = new RelatedNodesTransactionListener();
	}
	
	@Override
	public void onCreateNode(ChildAssociationRef childAssocRef) {
		
        final NodeRef uploadedNodeRef = childAssocRef.getChildRef();
        
        if (nodeService.exists(uploadedNodeRef) && isContentDoc(uploadedNodeRef)) {
        	
        	NodeRef previouslyExistentDoc = existedPreviousDocument(uploadedNodeRef);
        	
        	if (previouslyExistentDoc != null) {
        		
        		AlfrescoTransactionSupport.bindListener(transactionListener);
        		Pair<NodeRef, NodeRef> pair = new Pair<NodeRef, NodeRef>(uploadedNodeRef, previouslyExistentDoc);
        		AlfrescoTransactionSupport.bindResource(KEY_RELATED_NODE, pair);
        		
        		nodeService.addAspect(uploadedNodeRef, ContentModel.ASPECT_HIDDEN, null);
        		nodeService.addAspect(uploadedNodeRef, ContentModel.ASPECT_TEMPORARY, null);
        		
        	}
        	
        }
        
	}
	
    private boolean isContentDoc(NodeRef nodeRef) {
    	return nodeService.getType(nodeService.getPrimaryParent(nodeRef).getParentRef()).isMatch(ContentModel.TYPE_FOLDER);
    }
    
    private NodeRef existedPreviousDocument(NodeRef currentNodeRef) {
    	
    	String fileName = cleanNumberedSuffixes(nodeService.getProperty(currentNodeRef, ContentModel.PROP_NAME).toString());
    	NodeRef folder = nodeService.getPrimaryParent(currentNodeRef).getParentRef();
    	
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

	private class RelatedNodesTransactionListener extends TransactionListenerAdapter implements TransactionListener {

        @SuppressWarnings("unchecked")
		@Override
        public void afterCommit() {
        	Pair<NodeRef, NodeRef> pair = (Pair<NodeRef, NodeRef>) AlfrescoTransactionSupport.getResource(KEY_RELATED_NODE);
        	Runnable runnable = new RelatedNodeVersion(pair.getFirst(), pair.getSecond());
        	threadPoolExecutor.execute(runnable);
        }
        
		@Override
		public void flush() {
		}
    	
    }
    
	private class RelatedNodeVersion implements Runnable {
    	
    	private NodeRef contentNode;
    	private NodeRef nodeToBeVersioned;
    	
    	private RelatedNodeVersion(NodeRef contentNode, NodeRef nodeToBeVersioned) {
    		this.contentNode = contentNode;
    		this.nodeToBeVersioned = nodeToBeVersioned;
    	}

		@Override
		public void run() {
        	AuthenticationUtil.runAsSystem(new RunAsWork<Void>() {
        		
        		public Void doWork() throws Exception {
        			
        			RetryingTransactionCallback<Void> callback = new RetryingTransactionCallback<Void>() {
        				
        				@Override
        				public Void execute() throws Throwable {
        					
        					ContentReader reader = contentService.getReader(contentNode, ContentModel.PROP_CONTENT);
        					ContentWriter writer = contentService.getWriter(nodeToBeVersioned, ContentModel.PROP_CONTENT, true);
        					
        					writer.putContent(reader);
        					
        					nodeService.deleteNode(contentNode);
        					
					        return null;
        				}
        			};
        			
        			try {
        				RetryingTransactionHelper txnHelper = transactionService.getRetryingTransactionHelper();
        				txnHelper.doInTransaction(callback, false, true);
        			} catch (Throwable e) {
        				e.printStackTrace();
        			}
        			
			        return null;
        			
        		}
        	});
		}
    	
    }

	public void setPolicyComponent(PolicyComponent policyComponent) {
		this.policyComponent = policyComponent;
	}


	public void setTransactionListener(TransactionListener transactionListener) {
		this.transactionListener = transactionListener;
	}


	public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
		this.threadPoolExecutor = threadPoolExecutor;
	}


	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}


	public void setTransactionService(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	public void setContentService(ContentService contentService) {
		this.contentService = contentService;
	}

}

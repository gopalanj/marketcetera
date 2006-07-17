package org.marketcetera.photon;

import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;
import org.eclipse.swt.widgets.Display;
import org.marketcetera.core.ClassVersion;
import org.marketcetera.core.IDFactory;
import org.marketcetera.core.LoggerAdapter;
import org.marketcetera.core.MarketceteraException;
import org.marketcetera.core.NoMoreIDsException;
import org.marketcetera.photon.actions.CommandEvent;
import org.marketcetera.photon.actions.ICommandListener;
import org.marketcetera.photon.model.FIXMessageHistory;
import org.marketcetera.quickfix.FIXDataDictionaryManager;
import org.marketcetera.quickfix.FIXMessageUtil;
import org.marketcetera.quickfix.MarketceteraFIXException;

import quickfix.DataDictionary;
import quickfix.FieldNotFound;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.StringField;
import quickfix.field.ClOrdID;
import quickfix.field.CxlRejReason;
import quickfix.field.ExecID;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;
import quickfix.field.OrderID;
import quickfix.field.OrigClOrdID;
import quickfix.field.Symbol;
import quickfix.field.Text;

/**
 * $Id$
 * 
 * @author gmiller
 */
@ClassVersion("$Id$")
public class OrderManager {
	private IDFactory idFactory;
	
	private Logger internalMainLogger = Application.getMainConsoleLogger();


	private ICommandListener commandListener;

	private List<IOrderActionListener> orderActionListeners = new ArrayList<IOrderActionListener>();

	private FIXMessageHistory fixMessageHistory;
	

	/** Creates a new instance of OrderManager 
	 * @param fixMessageHistory */
	public OrderManager(IDFactory idFactory, FIXMessageHistory fixMessageHistory) {
		this.idFactory = idFactory;
		this.fixMessageHistory = fixMessageHistory;

		commandListener = new ICommandListener() {
			public void commandIssued(CommandEvent evt) {
				handleCommandIssued(evt);
			};
		};

	}

	public MessageListener getMessageListener() {
		return new MessageListener() {
			public void onMessage(javax.jms.Message message) {
				if (message instanceof TextMessage) {
					TextMessage textMessage = (TextMessage) message;
					try {
						final quickfix.Message qfMessage = new Message(textMessage
								.getText());
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								handleCounterpartyMessage(qfMessage);
							}
						});
					} catch (InvalidMessage e) {
						Application.getMainConsoleLogger().error("Exception processing incoming message", e);
					} catch (JMSException e) {
						Application.getMainConsoleLogger().error("Exception processing incoming message", e);
					}
				}
			}
		};

	}


	public void handleCounterpartyMessages(Object[] messages) {
		for (int i = 0; i < messages.length; i++) {
			Object object = messages[i];
			if (object instanceof Message) {
				Message aMessage = (Message) object;
				handleCounterpartyMessage(aMessage);
			}
		}
	}

	public void handleInternalMessages(Object[] messages) {
		for (int i = 0; i < messages.length; i++) {
			try {
				Object object = messages[i];
				if (object instanceof Message) {
					Message aMessage = (Message) object;
					handleInternalMessage(aMessage);
				}
			} catch (NoMoreIDsException ex) {
				internalMainLogger
						.error(
								"Could not get new ID's from database. Really bad.",
								ex);
			} catch (FieldNotFound e) {
				// TODO: fix this
				internalMainLogger.error("Error doing stuff", e);
			} catch (MarketceteraException e) {
				// TODO: fix this
				internalMainLogger.error("Error doing stuff", e);
			} catch (JMSException e) {
				// TODO: fix this
				internalMainLogger.error("Error doing stuff", e);
			}
		}
	}


	public void handleCounterpartyMessage(Message aMessage) {
		fixMessageHistory.addIncomingMessage(aMessage);
		fireOrderActionOccurred(aMessage);
		try {
			if (FIXMessageUtil.isExecutionReport(aMessage)) {
				handleExecutionReport(aMessage);
			} else if (FIXMessageUtil.isCancelReject(aMessage)) {
				handleCancelReject(aMessage);
			}
		} catch (FieldNotFound fnfEx) {
			MarketceteraFIXException mfix = MarketceteraFIXException.createFieldNotFoundException(fnfEx);
			internalMainLogger.error(
					"Error decoding incoming message "+mfix.getMessage(), mfix);
			mfix.printStackTrace();
		} catch (Throwable ex) {
			internalMainLogger.error(
					"Error decoding incoming message "+ex.getMessage(), ex);
			ex.printStackTrace();
		}
	}

	private void handleExecutionReport(Message aMessage) throws FieldNotFound, NoMoreIDsException {
		String orderID;
		ClOrdID clOrdID = new ClOrdID();
		aMessage.getField(clOrdID);
		orderID = clOrdID.getValue();
		char ordStatus = aMessage.getChar(OrdStatus.FIELD);

		if (ordStatus == OrdStatus.REJECTED) {
			String rejectReason = "";
			if(aMessage.isSetField(Text.FIELD)) {
				rejectReason = ": "+aMessage.getString(Text.FIELD);
			}
			
			String rejectMsg = "Order rejected " + orderID + " "
					+ aMessage.getString(Symbol.FIELD) + rejectReason;
			internalMainLogger.info(rejectMsg);
		}
	}


	private void handleCancelReject(Message aMessage) throws FieldNotFound {
		String reason = null;
		try {
			reason = aMessage.getString(CxlRejReason.FIELD);
		} catch (FieldNotFound fnf){
			//do nothing
		}
		String text = aMessage.getString(Text.FIELD);
		String origClOrdID = aMessage.getString(OrigClOrdID.FIELD);
		String errorMsg = "Cancel rejected for order " + origClOrdID + ": "
				+ (text == null ? "" : text)
				+ (reason == null ? "" : " (" + reason + ")");
		internalMainLogger.error(errorMsg);
	}





	public void handleInternalMessage(Message aMessage) throws FieldNotFound,
			MarketceteraException, JMSException {
		fireOrderActionOccurred(aMessage);

		if (FIXMessageUtil.isOrderSingle(aMessage)) {
			addNewOrder(aMessage);
		} else if (FIXMessageUtil.isCancelRequest(aMessage)) {
			cancelOneOrder(aMessage);
		} else if (FIXMessageUtil.isCancelReplaceRequest(aMessage)) {
			cancelReplaceOneOrder(aMessage);
		} else if (FIXMessageUtil.isCancelRequest(aMessage)) {
			cancelOneOrder(aMessage);
		}

	}

	protected void addNewOrder(Message aMessage) throws FieldNotFound,
			MarketceteraException {
		
		try {
			fixMessageHistory.addOutgoingMessage(aMessage);

			sendToApplicationQueue(aMessage);
		} catch (JMSException ex) {
			internalMainLogger.error(
					"Error sending message to JMS", ex);
		}
	}



	protected void cancelReplaceOneOrder(Message cancelMessage)
			throws NoMoreIDsException, FieldNotFound, JMSException {

		String clOrdId = (String) cancelMessage.getString(OrigClOrdID.FIELD);
		Message latestMessage = fixMessageHistory.getLatestMessage(clOrdId);
		if (latestMessage != null){
			cancelMessage.setField(new OrigClOrdID(clOrdId));
			cancelMessage.setField(new ClOrdID(this.idFactory.getNext()));
			fillFieldsFromExistingMessage(cancelMessage, latestMessage);

			fixMessageHistory.addOutgoingMessage(cancelMessage);
			try {
				sendToApplicationQueue(cancelMessage);
			} catch (JMSException e) {
				internalMainLogger.error("Error sending cancel/replace for order "+clOrdId, e);
			}
		} else {
			internalMainLogger.error("Could not send cancel/replace request for order ID "+clOrdId);
		}
	}

	protected void cancelOneOrder(Message cancelMessage)
			throws NoMoreIDsException, FieldNotFound, JMSException {
		String clOrdId = (String) cancelMessage.getString(OrigClOrdID.FIELD);
		cancelOneOrderByClOrdID(clOrdId);
	}
	
	public void cancelOneOrderByClOrdID(String clOrdID) throws NoMoreIDsException {
		Message latestMessage = fixMessageHistory.getLatestExecutionReport(clOrdID);
		if (latestMessage == null){
			latestMessage = fixMessageHistory.getLatestMessage(clOrdID);
			if (latestMessage == null){
				internalMainLogger.error("Could not send cancel request for order ID "+clOrdID);
				return;
			}
		}
		try { LoggerAdapter.debug("Exec id for cancel execution report:"+latestMessage.getString(ExecID.FIELD), this); } catch (FieldNotFound e1) {	}
			Message cancelMessage = new quickfix.fix42.Message();
			cancelMessage.getHeader().setString(MsgType.FIELD, MsgType.ORDER_CANCEL_REQUEST);
			cancelMessage.setField(new OrigClOrdID(clOrdID));
			cancelMessage.setField(new ClOrdID(this.idFactory.getNext()));
			try {
				cancelMessage.setField(new OrderID(latestMessage.getString(OrderID.FIELD)));
			} catch (FieldNotFound e) {
				// do nothing
			}
			fillFieldsFromExistingMessage(cancelMessage, latestMessage);

			fixMessageHistory.addOutgoingMessage(cancelMessage);
			try {
				sendToApplicationQueue(cancelMessage);
			} catch (JMSException e) {
				internalMainLogger.error("Error sending cancel for order "+clOrdID, e);
			}
	}

	private void fillFieldsFromExistingMessage(Message outgoingMessage, Message existingMessage){
		try {
			String msgType = outgoingMessage.getHeader().getString(MsgType.FIELD);
		    DataDictionary dict = FIXDataDictionaryManager.getDictionary();
		    for (int fieldInt = 1; fieldInt < 2000; fieldInt++){
			    if (dict.isRequiredField(msgType, fieldInt) && existingMessage.isSetField(fieldInt) &&
			    		!outgoingMessage.isSetField(fieldInt)){
			    	try {
			    		outgoingMessage.setField(existingMessage.getField(new StringField(fieldInt)));
			    	} catch (FieldNotFound e) {
						// do nothing
					}
			    }
		    }

		} catch (FieldNotFound ex) {
			internalMainLogger.error(
					"Outgoing message did not have valid MsgType ", ex);
		}
	}
	


	public IDFactory getIDFactory() {
		return idFactory;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.marketcetera.photon.actions.ICommandListener#commandIssued(org.marketcetera.photon.actions.CommandEvent)
	 */
	public void handleCommandIssued(CommandEvent evt) {
		try {
			if (evt.getDestination() == CommandEvent.Destination.BROKER){
				handleInternalMessage(evt.getMessage());
			}
		} catch (Exception e) {
			this.internalMainLogger.error("Error processing command", e);
		}
	}

	/**
	 * @return Returns the commandListener.
	 */
	public ICommandListener getCommandListener() {
		return commandListener;
	}

	/* (non-Javadoc)
	 * @see java.util.List#add(E)
	 */
	public boolean addOrderActionListener(IOrderActionListener arg0) {
		return orderActionListeners.add(arg0);
	}

	/* (non-Javadoc)
	 * @see java.util.List#remove(int)
	 */
	public boolean removeOrderActionListener(IOrderActionListener arg0) {
		return orderActionListeners.remove(arg0);
	}
	
	protected void fireOrderActionOccurred(Message fixMessage){
		for (IOrderActionListener listener : orderActionListeners) {
			try {
				listener.orderActionTaken(fixMessage);
			} catch (Exception ex){
				internalMainLogger.error("Error notifying IOrderActionListener", ex);
			}
		}
	}


	protected void sendToApplicationQueue(Message message) throws JMSException
	{
		Application.sendToQueue(message);
	}
	
	/**
	 * @return Returns the mainConsoleLogger.
	 */
	public Logger getMainConsoleLogger() {
		return internalMainLogger;
	}

	/**
	 * @param mainConsoleLogger The mainConsoleLogger to set.
	 */
	public void setMainConsoleLogger(Logger mainConsoleLogger) {
		this.internalMainLogger = mainConsoleLogger;
	}

}

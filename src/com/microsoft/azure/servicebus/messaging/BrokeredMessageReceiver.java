package com.microsoft.azure.servicebus.messaging;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;

import com.microsoft.azure.servicebus.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.MessageReceiver;
import com.microsoft.azure.servicebus.MessageWithDeliveryTag;
import com.microsoft.azure.servicebus.MessagingFactory;
import com.microsoft.azure.servicebus.ServiceBusException;
import com.microsoft.azure.servicebus.SettleModePair;
import com.microsoft.azure.servicebus.StringUtil;


// TODO As part of receive, don't return messages whose lock is already expired. Can happen because of delay between prefetch and actual receive from client.
class BrokeredMessageReceiver extends InitializableEntity implements IMessageReceiver
{
	private static final int DEFAULT_PREFETCH_COUNT = 100;
	
	private final ReceiveMode receiveMode;
	private boolean ownsMessagingFactory;
	private ConnectionStringBuilder amqpConnectionStringBuilder = null;
	private String entityPath = null;
	private MessagingFactory messagingFactory = null;
	private MessageReceiver internalReceiver = null;
	private boolean isInitialized = false;
	private int prefetchCount = DEFAULT_PREFETCH_COUNT;
	
	private BrokeredMessageReceiver(ReceiveMode receiveMode)
	{
		super(StringUtil.getRandomString(), null);
		this.receiveMode = receiveMode;
	}
	
	BrokeredMessageReceiver(ConnectionStringBuilder amqpConnectionStringBuilder, ReceiveMode receiveMode)
	{
		this(receiveMode);
		
		this.amqpConnectionStringBuilder = amqpConnectionStringBuilder;
		this.entityPath = this.amqpConnectionStringBuilder.getEntityPath();
		this.ownsMessagingFactory = true;
	}
	
	BrokeredMessageReceiver(MessagingFactory messagingFactory, String entityPath, ReceiveMode receiveMode)
	{		
		this(messagingFactory, entityPath, false, receiveMode);
	}
			
	private BrokeredMessageReceiver(MessagingFactory messagingFactory, String entityPath, boolean ownsMessagingFactory, ReceiveMode receiveMode)
	{		
		this(receiveMode);
		
		this.messagingFactory = messagingFactory;
		this.entityPath = entityPath;
		this.ownsMessagingFactory = ownsMessagingFactory;
	}
	
	@Override
	synchronized CompletableFuture<Void> initializeAsync() throws IOException
	{
		if(this.isInitialized)
		{
			return CompletableFuture.completedFuture(null);
		}
		else
		{
			CompletableFuture<Void> factoryFuture;
			if(this.messagingFactory == null)
			{
				factoryFuture = MessagingFactory.createFromConnectionStringBuilderAsync(amqpConnectionStringBuilder).thenAccept((f) -> {BrokeredMessageReceiver.this.messagingFactory = f;});
			}
			else
			{
				factoryFuture = CompletableFuture.completedFuture(null);
			}
			
			return factoryFuture.thenCompose((v) ->
			{
				CompletableFuture<MessageReceiver> receiverFuture = MessageReceiver.create(BrokeredMessageReceiver.this.messagingFactory, StringUtil.getRandomString(), BrokeredMessageReceiver.this.entityPath, this.prefetchCount, getSettleModePairForRecevieMode(this.receiveMode));
				return receiverFuture.thenAccept((r) -> 
				{
					BrokeredMessageReceiver.this.internalReceiver = r;
					BrokeredMessageReceiver.this.isInitialized = true;
				});
			});
		}
	}
	
	@Override
	public String getEntityPath() {
		return this.entityPath;
	}

	@Override
	public ReceiveMode getReceiveMode() {
		return this.receiveMode;
	}

	@Override
	public void abandon(IBrokeredMessage message) throws InterruptedException, ServiceBusException {
		Utils.completeFuture(this.abandonAsync(message));		
	}

	@Override
	public void abandon(IBrokeredMessage message, Map<String, Object> propertiesToModify) throws InterruptedException, ServiceBusException {
		Utils.completeFuture(this.abandonAsync(message, propertiesToModify));		
	}

	@Override
	public CompletableFuture<Void> abandonAsync(IBrokeredMessage message) {
		return this.abandonAsync(message, null);
	}

	@Override
	public CompletableFuture<Void> abandonAsync(IBrokeredMessage message, Map<String, Object> propertiesToModify) {
		this.ensurePeekLockReceiveMode();
		return this.internalReceiver.abandonMessageAsync(((BrokeredMessage)message).getDeliveryTag(), propertiesToModify);
	}

	@Override
	public void complete(IBrokeredMessage message) throws InterruptedException, ServiceBusException {
		Utils.completeFuture(this.completeAsync(message));
	}

	@Override
	public void completeBatch(Collection<? extends IBrokeredMessage> messages) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CompletableFuture<Void> completeAsync(IBrokeredMessage message) {
		this.ensurePeekLockReceiveMode();
		return this.internalReceiver.completeMessageAsync(((BrokeredMessage)message).getDeliveryTag());
	}

	@Override
	public CompletableFuture<Void> completeBatchAsync(Collection<? extends IBrokeredMessage> messages) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void defer(IBrokeredMessage message) throws InterruptedException, ServiceBusException
	{
		Utils.completeFuture(this.deferAsync(message));		
	}

	@Override
	public void defer(IBrokeredMessage message, Map<String, Object> propertiesToModify) throws InterruptedException, ServiceBusException 
	{
		Utils.completeFuture(this.deferAsync(message, propertiesToModify));		
	}

	@Override
	public CompletableFuture<Void> deferAsync(IBrokeredMessage message)
	{
		return this.deferAsync(message, null);
	}

	@Override
	public CompletableFuture<Void> deferAsync(IBrokeredMessage message, Map<String, Object> propertiesToModify)
	{
		this.ensurePeekLockReceiveMode();
		return this.internalReceiver.deferMessageAsync(((BrokeredMessage)message).getDeliveryTag(), propertiesToModify);
	}

	@Override
	public void deadLetter(IBrokeredMessage message) throws InterruptedException, ServiceBusException {
		Utils.completeFuture(this.deadLetterAsync(message));
	}

	@Override
	public void deadLetter(IBrokeredMessage message, Map<String, Object> propertiesToModify) throws InterruptedException, ServiceBusException {
		Utils.completeFuture(this.deadLetterAsync(message, propertiesToModify));		
	}

	@Override
	public void deadLetter(IBrokeredMessage message, String deadLetterReason, String deadLetterErrorDescription) throws InterruptedException, ServiceBusException {
		Utils.completeFuture(this.deadLetterAsync(message, deadLetterReason, deadLetterErrorDescription));		
	}
	
	@Override
	public void deadLetter(IBrokeredMessage message, String deadLetterReason, String deadLetterErrorDescription, Map<String, Object> propertiesToModify) throws InterruptedException, ServiceBusException {
		Utils.completeFuture(this.deadLetterAsync(message, deadLetterReason, deadLetterErrorDescription, propertiesToModify));
	}	

	@Override
	public CompletableFuture<Void> deadLetterAsync(IBrokeredMessage message) {
		return this.deadLetterAsync(message, null, null, null);
	}

	@Override
	public CompletableFuture<Void> deadLetterAsync(IBrokeredMessage message, Map<String, Object> propertiesToModify) {
		return this.deadLetterAsync(message, null, null, propertiesToModify);
	}

	@Override
	public CompletableFuture<Void> deadLetterAsync(IBrokeredMessage message, String deadLetterReason, String deadLetterErrorDescription)
	{
		return this.deadLetterAsync(message, deadLetterReason, deadLetterErrorDescription, null);
	}
	
	@Override
	public CompletableFuture<Void> deadLetterAsync(IBrokeredMessage message, String deadLetterReason, String deadLetterErrorDescription, Map<String, Object> propertiesToModify) {
		this.ensurePeekLockReceiveMode();
		return this.internalReceiver.deadLetterMessageAsync(((BrokeredMessage)message).getDeliveryTag(), deadLetterReason, deadLetterErrorDescription, propertiesToModify);
	}

	@Override
	public IBrokeredMessage receive() throws InterruptedException, ServiceBusException {
		return Utils.completeFuture(this.receiveAsync());
	}

	@Override
	public IBrokeredMessage receive(Duration serverWaitTime) throws InterruptedException, ServiceBusException{
		return Utils.completeFuture(this.receiveAsync(serverWaitTime));
	}

	@Override
	public IBrokeredMessage receive(long sequenceNumber) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<IBrokeredMessage> receiveBatch(int maxMessageCount) throws InterruptedException, ServiceBusException {
		return Utils.completeFuture(this.receiveBatchAsync(maxMessageCount));
	}

	@Override
	public Collection<IBrokeredMessage> receiveBatch(int maxMessageCount, Duration serverWaitTime) throws InterruptedException, ServiceBusException {
		return Utils.completeFuture(this.receiveBatchAsync(maxMessageCount, serverWaitTime));
	}

	@Override
	public Collection<IBrokeredMessage> receiveBatch(Collection<Long> sequenceNumbers) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<IBrokeredMessage> receiveAsync() {
		return this.internalReceiver.receiveAsync(1).thenApply(c -> 
		{	
			if(c == null)
				return null;
			else if (c.isEmpty())
				return null;
			else		
				return MessageConverter.convertAmqpMessageToBrokeredMessage(c.toArray(new MessageWithDeliveryTag[0])[0]);
		});
	}

	@Override
	public CompletableFuture<IBrokeredMessage> receiveAsync(Duration serverWaitTime) {
		return this.internalReceiver.receiveAsync(1, serverWaitTime).thenApply(c -> 
		{	
			if(c == null)
				return null;
			else if (c.isEmpty())
				return null;
			else		
				return MessageConverter.convertAmqpMessageToBrokeredMessage(c.toArray(new MessageWithDeliveryTag[0])[0]);
		});
	}

	@Override
	public CompletableFuture<IBrokeredMessage> receiveAsync(long sequenceNumber) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<Collection<IBrokeredMessage>> receiveBatchAsync(int maxMessageCount) {
		return this.internalReceiver.receiveAsync(maxMessageCount).thenApply(c -> 
		{	
			if(c == null)
				return null;
			else if (c.isEmpty())
				return null;
			else		
				return convertAmqpMessagesToBrokeredMessages(c);
		});
	}

	@Override
	public CompletableFuture<Collection<IBrokeredMessage>> receiveBatchAsync(int maxMessageCount, Duration serverWaitTime) {
		return this.internalReceiver.receiveAsync(maxMessageCount, serverWaitTime).thenApply(c -> 
		{	
			if(c == null)
				return null;
			else if (c.isEmpty())
				return null;
			else		
				return convertAmqpMessagesToBrokeredMessages(c);
		});
	}

	@Override
	public CompletableFuture<Collection<IBrokeredMessage>> receiveBatchAsync(Collection<Long> sequenceNumbers) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected CompletableFuture<Void> onClose() {
		if(this.isInitialized)
		{
			return this.internalReceiver.closeAsync().thenComposeAsync((v) -> 
			{
				if(BrokeredMessageReceiver.this.ownsMessagingFactory)
				{
					return BrokeredMessageReceiver.this.messagingFactory.closeAsync();
				}
				else
				{
					return CompletableFuture.completedFuture(null);
				}				
			});
		}
		else
		{
			return CompletableFuture.completedFuture(null);			
		}
	}

	@Override
	public int getPrefetchCount()
	{
		return this.prefetchCount;
	}

	@Override
	public void setPrefetchCount(int prefetchCount) throws ServiceBusException
	{
		this.prefetchCount = prefetchCount;
		if(this.isInitialized)
		{
			this.internalReceiver.setPrefetchCount(prefetchCount);
		}
	}
	
	private static SettleModePair getSettleModePairForRecevieMode(ReceiveMode receiveMode)
	{
		if(receiveMode == ReceiveMode.ReceiveAndDelete)
		{
			return new SettleModePair(SenderSettleMode.SETTLED, ReceiverSettleMode.FIRST);
		}
		else
		{
			return new SettleModePair(SenderSettleMode.UNSETTLED, ReceiverSettleMode.SECOND);
		}
	}
	
	private Collection<IBrokeredMessage> convertAmqpMessagesToBrokeredMessages(Collection<MessageWithDeliveryTag> amqpMessages)
	{
		ArrayList<IBrokeredMessage> convertedMessages = new ArrayList<IBrokeredMessage>();
		for(MessageWithDeliveryTag amqpMessageWithDeliveryTag : amqpMessages)
		{
			convertedMessages.add(MessageConverter.convertAmqpMessageToBrokeredMessage(amqpMessageWithDeliveryTag));
		}
		
		return convertedMessages;
	}
	
	private void ensurePeekLockReceiveMode()
	{
		if(this.receiveMode != ReceiveMode.PeekLock)
		{
			throw new UnsupportedOperationException("Operations Complete/Abandon/DeadLetter/Defer cannot be called on a receiver opened in ReceiveAndDelete mode.");
		}
	}
}

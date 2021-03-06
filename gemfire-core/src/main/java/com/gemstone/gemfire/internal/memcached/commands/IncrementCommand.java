/*=========================================================================
 * Copyright (c) 2010-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * one or more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */
package com.gemstone.gemfire.internal.memcached.commands;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.TimeUnit;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.internal.memcached.KeyWrapper;
import com.gemstone.gemfire.internal.memcached.Reply;
import com.gemstone.gemfire.internal.memcached.RequestReader;
import com.gemstone.gemfire.internal.memcached.ResponseStatus;
import com.gemstone.gemfire.internal.memcached.ValueWrapper;
import com.gemstone.gemfire.memcached.GemFireMemcachedServer.Protocol;

/**
 * <code>
 * incr &lt;key&gt; &lt;value&gt; [noreply]\r\n
 * </code><br/>
 * value is the amount by which the client wants to increase/decrease
 * the item. It is a decimal representation of a 64-bit unsigned integer.
 * 
 * The data for the item is
 * treated as decimal representation of a 64-bit unsigned integer.
 * Also, the item must already exist for incr/decr to work; these commands won't pretend
 * that a non-existent key exists with value 0; instead, they will fail.
 * 
 * @author Swapnil Bawaskar
 *
 */
public class IncrementCommand extends AbstractCommand {

  @Override
  public ByteBuffer processCommand(RequestReader request, Protocol protocol, Cache cache) {
    if (protocol == Protocol.ASCII) {
      return processAsciiCommand(request.getRequest(), cache);
    }
    return processBinaryProtocol(request, cache);
  }

  private ByteBuffer processAsciiCommand(ByteBuffer buffer, Cache cache) {
    CharBuffer flb = getFirstLineBuffer();
    getAsciiDecoder().reset();
    getAsciiDecoder().decode(buffer, flb, false);
    flb.flip();
    String firstLine = getFirstLine();
    String[] firstLineElements = firstLine.split(" ");
    
    assert "incr".equals(firstLineElements[0]);
    String key = firstLineElements[1];
    String incrByStr = stripNewline(firstLineElements[2]);
    Long incrBy = Long.parseLong(incrByStr);
    boolean noReply = firstLineElements.length > 3;
    
    Region<Object, ValueWrapper> r = getMemcachedRegion(cache);
    String reply = Reply.NOT_FOUND.toString();
    ByteBuffer newVal = ByteBuffer.allocate(8);
    while (true) {
      ValueWrapper oldValWrapper = r.get(key);
      if (oldValWrapper == null) {
        break;
      }
      newVal.clear();
      byte[] oldVal = oldValWrapper.getValue();
      long oldLong = getLongFromByteArray(oldVal);
      long newLong = oldLong + incrBy;
      newVal.putLong(0, newLong);
      ValueWrapper newValWrapper = ValueWrapper.getWrappedValue(newVal.array(), 0/*flags*/);
      if (r.replace(key, oldValWrapper, newValWrapper)) {
        reply = newLong + "\r\n";
        break;
      }
    }
    return noReply ? null : asciiCharset.encode(reply);
  }

  private static final int LONG_LENGTH = 8;
  
  private ByteBuffer processBinaryProtocol(RequestReader request, Cache cache) {
    ByteBuffer buffer = request.getRequest();
    int extrasLength = buffer.get(EXTRAS_LENGTH_INDEX);
    final KeyWrapper key = getKey(buffer, HEADER_LENGTH + extrasLength);
    
    long incrBy = buffer.getLong(HEADER_LENGTH);
    long initialVal = buffer.getLong(HEADER_LENGTH + LONG_LENGTH);
    int expiration = buffer.getInt(HEADER_LENGTH + LONG_LENGTH + LONG_LENGTH);
    
    final Region<Object, ValueWrapper> r = getMemcachedRegion(cache);
    ByteBuffer newVal = ByteBuffer.allocate(8);
    boolean notFound = false;
    ValueWrapper newValWrapper = null;
    
    try {
      while (true) {
        ValueWrapper oldValWrapper = r.get(key);
        if (oldValWrapper == null) {
          if (expiration == -1) {
            notFound = true;
          } else {
            newVal.putLong(0, initialVal);
            newValWrapper = ValueWrapper.getWrappedValue(newVal.array(), 0/*flags*/);
            r.put(key, newValWrapper);
          }
          break;
        }
        byte[] oldVal = oldValWrapper.getValue();
        long oldLong = getLongFromByteArray(oldVal);
        long newLong = oldLong + incrBy;
        newVal.putLong(0, newLong);
        newValWrapper = ValueWrapper.getWrappedValue(newVal.array(), 0/*flags*/);
        if (r.replace(key, oldValWrapper, newValWrapper)) {
          break;
        }
      }
    } catch (Exception e) {
      return handleBinaryException(key, request, request.getResponse(), "increment", e);
    }
    
    if (expiration > 0) {
      StorageCommand.getExpiryExecutor().schedule(new Runnable() {
        @Override
        public void run() {
          r.destroy(key);
        }
      }, expiration, TimeUnit.SECONDS);
    }
    
    if (getLogger().fineEnabled()) {
      getLogger().fine("incr:key:"+key+" incrBy:"+incrBy+" initVal:"+initialVal+" exp:"+expiration+" notFound:"+notFound);
    }
    
    ByteBuffer response = null;
    if (notFound) {
      response = request.getResponse();
      response.putShort(POSITION_RESPONSE_STATUS, ResponseStatus.KEY_NOT_FOUND.asShort());
    } else {
      if (isQuiet()) {
        return null;
      }
      response = request.getResponse(HEADER_LENGTH + LONG_LENGTH);
      response.putInt(TOTAL_BODY_LENGTH_INDEX, LONG_LENGTH);
      response.putLong(HEADER_LENGTH, newVal.getLong(0));
      response.putLong(POSITION_CAS, newValWrapper.getVersion());
    }
    return response;
  }
  
  /**
   * Overridden by Q command
   */
  protected boolean isQuiet() {
    return false;
  }
}

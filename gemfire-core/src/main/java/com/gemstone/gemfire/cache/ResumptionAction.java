/*=========================================================================
 * Copyright (c) 2002-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * more patents listed at http://www.pivotal.io/patents.
 *========================================================================
 */
package com.gemstone.gemfire.cache;

import com.gemstone.gemfire.internal.i18n.LocalizedStrings;

import java.io.*;
import java.util.*;

/**
 * Specifies how the region is affected by resumption of reliability when
 * one or more missing required roles return to the distributed membership.  
 * The <code>ResumptionAction</code> is specified when configuring a region's 
 * {@link com.gemstone.gemfire.cache.MembershipAttributes}.
 * 
 * @author Kirk Lund
 * @since 5.0
 */
public class ResumptionAction implements java.io.Serializable {
  private static final long serialVersionUID = 6632254151314915610L;

  /** No special action takes place when reliability resumes. */
  public static final ResumptionAction NONE = 
      new ResumptionAction("NONE");

  /** 
   * Resumption of reliability causes the region to be cleared of all data 
   * and {@link DataPolicy#withReplication replicated} regions will do a new
   * getInitialImage operation to repopulate the region.  Any existing 
   * references to this region become unusable in that any subsequent methods
   * invoked on those references will throw a {@link 
   * RegionReinitializedException}.
   */
  public static final ResumptionAction REINITIALIZE = 
      new ResumptionAction("REINITIALIZE");

  /** The name of this mirror type. */
  private final transient String name;

  // The 4 declarations below are necessary for serialization
  /** byte used as ordinal to represent this Scope */
  public final byte ordinal = nextOrdinal++;

  private static byte nextOrdinal = 0;
  
  private static final ResumptionAction[] PRIVATE_VALUES =
    { NONE, REINITIALIZE };

  /** List of all ResumptionAction values */
  public static final List VALUES = 
    Collections.unmodifiableList(Arrays.asList(PRIVATE_VALUES));
    
  private Object readResolve() throws ObjectStreamException {
    return PRIVATE_VALUES[ordinal];  // Canonicalize
  }
  
  /** Creates a new instance of ResumptionAction. */
  private ResumptionAction(String name) {
      this.name = name;
  }
  
  /** Return the ResumptionAction represented by specified ordinal */
  public static ResumptionAction fromOrdinal(byte ordinal) {
    return PRIVATE_VALUES[ordinal];
  }
  
  /** Return the ResumptionAction specified by name */
  public static ResumptionAction fromName(String name) {
    if (name == null || name.length() == 0) {
      throw new IllegalArgumentException(LocalizedStrings.ResumptionAction_INVALID_RESUMPTIONACTION_NAME_0.toLocalizedString(name));
    }
    for (int i = 0; i < PRIVATE_VALUES.length; i++) {
      if (name.equals(PRIVATE_VALUES[i].name)) {
        return PRIVATE_VALUES[i];
      }
    }
    throw new IllegalArgumentException(LocalizedStrings.ResumptionAction_INVALID_RESUMPTIONACTION_NAME_0.toLocalizedString(name));
  }
  
  /** Returns true if this is <code>NONE</code>. */
  public boolean isNone() {
    return this == NONE;
  }
  
  /** Returns true if this is <code>REINITIALIZE</code>. */
  public boolean isReinitialize() {
    return this == REINITIALIZE;
  }
  
  /** 
   * Returns a string representation for this resumption action.
   * @return the name of this resumption action
   */
  @Override
  public String toString() {
      return this.name;
  }
  
}


/* **************************************************************************************
 * Copyright (c) 2019 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.card.calypso;

import java.util.HashMap;
import java.util.Map;
import org.calypsonet.terminal.card.ApduResponseApi;
import org.eclipse.keyple.core.util.ApduUtil;
import org.eclipse.keyple.core.util.ByteArrayUtil;

/**
 * (package-private)<br>
 * Builds the Read Event Counter APDU command.
 *
 * @since 2.0.1
 */
final class CmdSamReadEventCounter extends AbstractSamCommand {
  /** The command reference. */
  private static final CalypsoSamCommand command = CalypsoSamCommand.READ_EVENT_COUNTER;

  /** Event counter operation type */
  enum CounterOperationType {
    /** Single counter */
    READ_SINGLE_COUNTER,
    /** Counter record */
    READ_COUNTER_RECORD
  }

  private final CounterOperationType counterOperationType;
  private final int firstEventCounterNumber;

  private static final Map<Integer, StatusProperties> STATUS_TABLE;

  static {
    Map<Integer, StatusProperties> m =
        new HashMap<Integer, StatusProperties>(AbstractSamCommand.STATUS_TABLE);
    m.put(
        0x6900,
        new StatusProperties(
            "An event counter cannot be incremented.", CalypsoSamCounterOverflowException.class));
    m.put(0x6A00, new StatusProperties("Incorrect P2.", CalypsoSamIllegalParameterException.class));
    m.put(0x6200, new StatusProperties("Correct execution with warning: data not signed."));
    STATUS_TABLE = m;
  }

  /**
   * (package-private)<br>
   * Instantiate a new CmdSamReadEventCounter
   *
   * @param calypsoSam The Calypso SAM.
   * @param counterOperationType the counter operation type.
   * @param target the counter index (0-26) if READ_SINGLE_COUNTER, the record index (1-3) if
   *     READ_COUNTER_RECORD.
   * @since 2.0.1
   */
  CmdSamReadEventCounter(
      CalypsoSamAdapter calypsoSam, CounterOperationType counterOperationType, int target) {

    super(command, 48, calypsoSam);

    byte cla = SamUtilAdapter.getClassByte(calypsoSam.getProductType());
    byte p2;
    this.counterOperationType = counterOperationType;
    if (counterOperationType == CounterOperationType.READ_SINGLE_COUNTER) {
      this.firstEventCounterNumber = target;
      p2 = (byte) (0x81 + target);
    } else {
      this.firstEventCounterNumber = (target - 1) * 9;
      p2 = (byte) (0xE0 + target);
    }

    setApduRequest(
        new ApduRequestAdapter(
            ApduUtil.build(cla, command.getInstructionByte(), (byte) 0x00, p2, null, (byte) 0x00)));
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0.1
   */
  @Override
  Map<Integer, StatusProperties> getStatusTable() {
    return STATUS_TABLE;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.2.3
   */
  @Override
  void parseApduResponse(ApduResponseApi apduResponse) throws CalypsoSamCommandException {
    super.parseApduResponse(apduResponse);
    byte[] dataOut = apduResponse.getDataOut();
    if (counterOperationType == CounterOperationType.READ_SINGLE_COUNTER) {
      getCalypsoSam().putEventCounter(dataOut[8], ByteArrayUtil.extractInt(dataOut, 9, 3, false));
    } else {
      for (int i = 0; i < 9; i++) {
        getCalypsoSam()
            .putEventCounter(
                firstEventCounterNumber + i,
                ByteArrayUtil.extractInt(dataOut, 8 + (3 * i), 3, false));
      }
    }
  }
}
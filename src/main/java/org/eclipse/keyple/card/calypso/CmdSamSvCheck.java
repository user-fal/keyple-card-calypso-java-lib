/* **************************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://calypsonet.org/
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

import static org.eclipse.keyple.card.calypso.DtoAdapters.*;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.keyple.core.util.ApduUtil;

/**
 * Builds the SV Check APDU command.
 *
 * @since 2.0.1
 */
final class CmdSamSvCheck extends SamCommand {

  private static final Map<Integer, StatusProperties> STATUS_TABLE;

  static {
    Map<Integer, StatusProperties> m =
        new HashMap<Integer, StatusProperties>(SamCommand.STATUS_TABLE);
    m.put(0x6700, new StatusProperties("Incorrect Lc.", SamIllegalParameterException.class));
    m.put(
        0x6985,
        new StatusProperties("No active SV transaction.", SamAccessForbiddenException.class));
    m.put(0x6988, new StatusProperties("Incorrect SV signature.", SamSecurityDataException.class));
    STATUS_TABLE = m;
  }

  /**
   * Instantiates a new CmdSamSvCheck to authenticate a card SV transaction.
   *
   * @param calypsoSam The Calypso SAM.
   * @param svCardSignature null if the operation is to abort the SV transaction, a 3 or 6-byte
   *     array. containing the card signature from SV Debit, SV Load or SV Undebit.
   * @since 2.0.1
   */
  CmdSamSvCheck(CalypsoSamAdapter calypsoSam, byte[] svCardSignature) {

    super(SamCommandRef.SV_CHECK, 0, calypsoSam);

    if (svCardSignature != null && (svCardSignature.length != 3 && svCardSignature.length != 6)) {
      throw new IllegalArgumentException("Invalid svCardSignature.");
    }

    byte cla = calypsoSam.getClassByte();
    byte p1 = (byte) 0x00;
    byte p2 = (byte) 0x00;

    if (svCardSignature != null) {
      // the operation is not "abort"
      byte[] data = new byte[svCardSignature.length];
      System.arraycopy(svCardSignature, 0, data, 0, svCardSignature.length);
      setApduRequest(
          new ApduRequestAdapter(
              ApduUtil.build(cla, getCommandRef().getInstructionByte(), p1, p2, data, null)));
    } else {
      setApduRequest(
          new ApduRequestAdapter(
              ApduUtil.build(
                  cla,
                  getCommandRef().getInstructionByte(),
                  p1,
                  p2,
                  new byte[0],
                  null))); // Case 3 without input data.
    }
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
}
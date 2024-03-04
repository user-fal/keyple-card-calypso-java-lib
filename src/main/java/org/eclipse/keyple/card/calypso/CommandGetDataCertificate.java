/* **************************************************************************************
 * Copyright (c) 2022 Calypso Networks Association https://calypsonet.org/
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
import org.eclipse.keypop.card.ApduResponseApi;

/**
 * Builds the Get data APDU commands for the CARD CERTIFICATE or CA CERTIFICATE tags.
 *
 * <p>In contact mode, this command can not be sent in a secure session because it would generate a
 * 6Cxx status and thus make calculation of the digest impossible.
 *
 * @since 3.1.0
 */
final class CommandGetDataCertificate extends Command {

  private static final Map<Integer, StatusProperties> STATUS_TABLE;

  static {
    Map<Integer, StatusProperties> m = new HashMap<Integer, StatusProperties>(Command.STATUS_TABLE);
    m.put(
        0x6A88,
        new StatusProperties(
            "Data object not found (optional mode not available).", CardDataAccessException.class));
    m.put(
        0x6B00,
        new StatusProperties("P1 or P2 value not supported.", CardDataAccessException.class));
    STATUS_TABLE = m;
  }

  private final boolean isCardCertificate;
  private final boolean isFirstPart;

  /**
   * Constructor.
   *
   * @param transactionContext The global transaction context common to all commands.
   * @param commandContext The local command context specific to each command.
   * @since 3.1.0
   */
  CommandGetDataCertificate(
      TransactionContextDto transactionContext,
      CommandContextDto commandContext,
      boolean isCardCertificate,
      boolean isFirstPart) {
    super(CardCommandRef.GET_DATA, 0, transactionContext, commandContext);
    this.isCardCertificate = isCardCertificate;
    this.isFirstPart = isFirstPart;
    byte cardClass =
        transactionContext.getCard() != null
            ? transactionContext.getCard().getCardClass().getValue()
            : CalypsoCardClass.ISO.getValue();
    byte p1;
    byte p2;
    if (isCardCertificate) {
      p1 = BerTlvTag.CARD_CERTIFICATE_MSB;
      p2 = BerTlvTag.CARD_CERTIFICATE_LSB;
    } else {
      p1 = BerTlvTag.CA_CERTIFICATE_MSB;
      p2 = BerTlvTag.CA_CERTIFICATE_LSB;
    }
    if (!isFirstPart) {
      p2++;
    }
    setApduRequest(
        new ApduRequestAdapter(
            ApduUtil.build(
                cardClass, getCommandRef().getInstructionByte(), p1, p2, null, (byte) 0x00)));
    if (isCardCertificate) {
      addSubName("CARD_CERTIFICATE");
    } else {
      addSubName("CA_CERTIFICATE");
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 3.1.0
   */
  @Override
  void finalizeRequest() {
    encryptRequestAndUpdateTerminalSessionMacIfNeeded();
  }

  /**
   * {@inheritDoc}
   *
   * @since 3.1.0
   */
  @Override
  boolean isCryptoServiceRequiredToFinalizeRequest() {
    return getCommandContext().isEncryptionActive();
  }

  /**
   * {@inheritDoc}
   *
   * @since 3.1.0
   */
  @Override
  boolean synchronizeCryptoServiceBeforeCardProcessing() {
    return !getCommandContext().isSecureSessionOpen();
  }

  /**
   * {@inheritDoc}
   *
   * @since 3.1.0
   */
  @Override
  void parseResponse(ApduResponseApi apduResponse) throws CardCommandException {
    decryptResponseAndUpdateTerminalSessionMacIfNeeded(apduResponse);
    super.setApduResponseAndCheckStatus(apduResponse);

    byte[] dataOut = apduResponse.getDataOut();
    byte[] certificateBytes;

    byte[] expectedTagBytes =
        isCardCertificate
            ? new byte[] {BerTlvTag.CARD_CERTIFICATE_MSB, BerTlvTag.CARD_CERTIFICATE_LSB}
            : new byte[] {BerTlvTag.CA_CERTIFICATE_MSB, BerTlvTag.CA_CERTIFICATE_LSB};

    if (isFirstPart) {
      // Check if the first 2 bytes of the response match the expected tag.
      if (dataOut.length >= 5
          && dataOut[0] == expectedTagBytes[0]
          && dataOut[1] == expectedTagBytes[1]) {
        // Extract the certificate bytes, skipping the 5-byte tag and length prefix.
        certificateBytes = new byte[dataOut.length - 5];
        System.arraycopy(dataOut, 5, certificateBytes, 0, dataOut.length - 5);
      } else {
        throw new CardDataAccessException(
            "Unexpected tag or length for " + (isCardCertificate ? "card" : "CA") + " certificate",
            getCommandRef());
      }
    } else {
      // For subsequent parts, the entire dataOut is assumed to be the certificate data.
      certificateBytes = dataOut;
    }

    if (certificateBytes == null) {
      throw new CardDataAccessException(
          "Invalid " + (isCardCertificate ? "card" : "CA") + " certificate", getCommandRef());
    }

    if (isCardCertificate) {
      getTransactionContext().getCard().addCardCertificateBytes(certificateBytes, isFirstPart);
    } else {
      getTransactionContext().getCard().addCaCertificateBytes(certificateBytes, isFirstPart);
    }

    updateTerminalSessionIfNeeded();
  }

  /**
   * {@inheritDoc}
   *
   * @since 3.1.0
   */
  @Override
  Map<Integer, StatusProperties> getStatusTable() {
    return STATUS_TABLE;
  }
}

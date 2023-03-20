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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.calypsonet.terminal.calypso.card.ElementaryFile;
import org.calypsonet.terminal.card.ApduResponseApi;
import org.eclipse.keyple.core.util.ApduUtil;
import org.eclipse.keyple.core.util.HexUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the Read Records APDU command.
 *
 * @since 2.0.1
 */
final class CmdCardReadRecords extends CardCommand {

  private static final Logger logger = LoggerFactory.getLogger(CmdCardReadRecords.class);

  private static final Map<Integer, StatusProperties> STATUS_TABLE;

  static {
    Map<Integer, StatusProperties> m =
        new HashMap<Integer, StatusProperties>(CardCommand.STATUS_TABLE);
    m.put(
        0x6981,
        new StatusProperties("Command forbidden on binary files", CardDataAccessException.class));
    m.put(
        0x6982,
        new StatusProperties(
            "Security conditions not fulfilled (PIN code not presented, encryption required).",
            CardSecurityContextException.class));
    m.put(
        0x6985,
        new StatusProperties(
            "Access forbidden (Never access mode, stored value log file and a stored value operation was done"
                + " during the current session).",
            CardAccessForbiddenException.class));
    m.put(
        0x6986,
        new StatusProperties("Command not allowed (no current EF)", CardDataAccessException.class));
    m.put(0x6A82, new StatusProperties("File not found", CardDataAccessException.class));
    m.put(
        0x6A83,
        new StatusProperties(
            "Record not found (record index is 0, or above NumRec", CardDataAccessException.class));
    m.put(
        0x6B00,
        new StatusProperties("P2 value not supported", CardIllegalParameterException.class));
    STATUS_TABLE = m;
  }

  /**
   * Indicates if one or multiple records
   *
   * @since 2.0.1
   */
  enum ReadMode {
    /** read one record */
    ONE_RECORD,
    /** read multiple records */
    MULTIPLE_RECORD
  }

  // Construction arguments used for parsing
  private int sfi;
  private int firstRecordNumber;
  private int recordSize;
  private ReadMode readMode;
  private transient boolean isPreOpenMode; // NOSONAR
  private transient byte[] anticipatedDataOut; // NOSONAR

  /**
   * Instantiates a new read records cmd build.
   *
   * @param calypsoCard The Calypso card.
   * @param sfi the sfi top select.
   * @param firstRecordNumber the record number to read (or first record to read in case of several.
   *     records)
   * @param readMode read mode, requests the reading of one or all the records.
   * @param expectedLength the expected length of the record(s).
   * @param recordSize the size of one record.
   * @since 2.2.3
   * @deprecated
   */
  @Deprecated
  CmdCardReadRecords(
      CalypsoCardAdapter calypsoCard,
      int sfi,
      int firstRecordNumber,
      ReadMode readMode,
      int expectedLength,
      int recordSize) {
    super(CardCommandRef.READ_RECORDS, expectedLength, calypsoCard, null, null);
    buildCommand(
        calypsoCard.getCardClass(), sfi, firstRecordNumber, readMode, expectedLength, recordSize);
  }

  /**
   * Instantiates a new read records cmd build.
   *
   * @param transactionContext The global transaction context common to all commands.
   * @param commandContext The local command context specific to each command.
   * @param sfi the sfi top select.
   * @param firstRecordNumber the record number to read (or first record to read in case of several.
   *     records)
   * @param readMode read mode, requests the reading of one or all the records.
   * @param expectedLength the expected length of the record(s).
   * @param recordSize the size of one record.
   * @since 2.3.2
   */
  CmdCardReadRecords(
      TransactionContextDto transactionContext,
      CommandContextDto commandContext,
      int sfi,
      int firstRecordNumber,
      ReadMode readMode,
      int expectedLength,
      int recordSize) {
    super(CardCommandRef.READ_RECORDS, expectedLength, null, transactionContext, commandContext);
    isPreOpenMode = transactionContext.getCard().getPreOpenWriteAccessLevel() != null;
    buildCommand(
        transactionContext.getCard().getCardClass(),
        sfi,
        firstRecordNumber,
        readMode,
        expectedLength,
        recordSize);
  }

  /**
   * Constructor (to be used for card selection only).
   *
   * @param sfi the sfi top select.
   * @param firstRecordNumber the record number to read (or first record to read in case of several.
   *     records)
   * @param readMode read mode, requests the reading of one or all the records.
   * @param expectedLength the expected length of the record(s).
   * @since 2.0.1
   */
  CmdCardReadRecords(int sfi, int firstRecordNumber, ReadMode readMode, int expectedLength) {
    super(CardCommandRef.READ_RECORDS, expectedLength, null, null, null);
    buildCommand(CalypsoCardClass.ISO, sfi, firstRecordNumber, readMode, expectedLength, 0);
  }

  /**
   * Builds the command.
   *
   * @param calypsoCardClass indicates which CLA byte should be used for the Apdu.
   * @param sfi the sfi top select.
   * @param firstRecordNumber the record number to read (or first record to read in case of several.
   *     records)
   * @param readMode read mode, requests the reading of one or all the records.
   * @param expectedLength the expected length of the record(s).
   * @param recordSize the size of one record.
   */
  private void buildCommand(
      CalypsoCardClass calypsoCardClass,
      int sfi,
      int firstRecordNumber,
      ReadMode readMode,
      int expectedLength,
      int recordSize) {

    this.sfi = sfi;
    this.firstRecordNumber = firstRecordNumber;
    this.recordSize = recordSize;
    this.readMode = readMode;

    byte p1 = (byte) firstRecordNumber;
    byte p2 = (sfi == (byte) 0x00) ? (byte) 0x05 : (byte) ((byte) (sfi * 8) + 5);
    if (readMode == ReadMode.ONE_RECORD) {
      p2 = (byte) (p2 - (byte) 0x01);
    }
    byte le = (byte) expectedLength;
    setApduRequest(
        new ApduRequestAdapter(
            ApduUtil.build(
                calypsoCardClass.getValue(),
                getCommandRef().getInstructionByte(),
                p1,
                p2,
                null,
                le)));

    if (logger.isDebugEnabled()) {
      String extraInfo =
          "SFI: "
              + Integer.toHexString(sfi)
              + "h, REC: "
              + firstRecordNumber
              + ", READMODE: "
              + readMode.name()
              + ", EXPECTEDLENGTH: "
              + expectedLength;
      addSubName(extraInfo);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @return false
   * @since 2.0.1
   */
  @Override
  boolean isSessionBufferUsed() {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  void finalizeRequest() {
    encryptRequestAndUpdateTerminalSessionMacIfNeeded();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  boolean isCryptoServiceRequiredToFinalizeRequest() {
    return getCommandContext().isEncryptionActive();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  boolean synchronizeCryptoServiceBeforeCardProcessing() {
    if (!getCommandContext().isSecureSessionOpen()) {
      return true; // Nothing to synchronize
    }
    if (getCommandContext().isEncryptionActive()) {
      return false;
    }
    if (!isPreOpenMode) {
      return false;
    }
    // Pre-open mode without encryption in secure session
    if (!isCryptoServiceSynchronized()) {
      byte[] anticipatedApduResponse = buildAnticipatedResponse();
      if (anticipatedApduResponse == null) {
        logger.warn(
            "Unable to determine the anticipated APDU response for the command '{}' (SFI {}h, record {})"
                + " because the record or some records have not been read beforehand.",
            getName(),
            HexUtil.toHex(sfi),
            firstRecordNumber);
        return false;
      }
      anticipatedDataOut =
          Arrays.copyOf(anticipatedApduResponse, anticipatedApduResponse.length - 2);
      updateTerminalSessionMacIfNeeded(anticipatedApduResponse);
    }
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.3.2
   */
  @Override
  void parseResponse(ApduResponseApi apduResponse) throws CardCommandException {
    decryptResponseAndUpdateTerminalSessionMacIfNeeded(apduResponse);
    if (!setApduResponseAndCheckStatusInBestEffortMode(apduResponse)) {
      return;
    }
    byte[] dataOut = apduResponse.getDataOut();
    if (readMode == CmdCardReadRecords.ReadMode.ONE_RECORD) {
      getTransactionContext().getCard().setContent((byte) sfi, firstRecordNumber, dataOut);
    } else {
      int apduLen = dataOut.length;
      int index = 0;
      while (apduLen > 0) {
        byte recordNb = dataOut[index++];
        byte len = dataOut[index++];
        getTransactionContext()
            .getCard()
            .setContent((byte) sfi, recordNb, Arrays.copyOfRange(dataOut, index, index + len));
        index = index + len;
        apduLen = apduLen - 2 - len;
      }
    }
    if (!isCryptoServiceSynchronized()) {
      updateTerminalSessionMacIfNeeded();
    } else if (getCommandContext().isSecureSessionOpen()
        && isPreOpenMode
        && !Arrays.equals(dataOut, anticipatedDataOut)) {
      throw new CardSecurityContextException(
          "Data out does not match the anticipated data out", CardCommandRef.READ_RECORDS);
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

  /**
   * {@inheritDoc}
   *
   * @since 2.1.0
   */
  @Override
  void setApduResponseAndCheckStatus(ApduResponseApi apduResponse) throws CardCommandException {
    super.setApduResponseAndCheckStatus(apduResponse);
    if (readMode == CmdCardReadRecords.ReadMode.ONE_RECORD) {
      getCalypsoCard().setContent((byte) sfi, firstRecordNumber, apduResponse.getDataOut());
    } else {
      byte[] apdu = apduResponse.getDataOut();
      int apduLen = apdu.length;
      int index = 0;
      while (apduLen > 0) {
        byte recordNb = apdu[index++];
        byte len = apdu[index++];
        getCalypsoCard()
            .setContent((byte) sfi, recordNb, Arrays.copyOfRange(apdu, index, index + len));
        index = index + len;
        apduLen = apduLen - 2 - len;
      }
    }
  }

  /**
   * Builds the anticipated APDU response with the SW.
   *
   * @return Null if the record or some records have not been read beforehand.
   */
  private byte[] buildAnticipatedResponse() {
    ElementaryFile ef = getTransactionContext().getCard().getFileBySfi((byte) sfi);
    if (ef == null) {
      return null; // NOSONAR
    }
    return readMode == CmdCardReadRecords.ReadMode.ONE_RECORD
        ? buildAnticipatedResponseForOneRecordMode(ef)
        : buildAnticipatedResponseForMultipleRecordsMode(ef);
  }

  /**
   * Builds the anticipated APDU response with the SW for single record mode.
   *
   * @param ef The EF.
   * @return Null if the record has not been read beforehand.
   */
  private byte[] buildAnticipatedResponseForOneRecordMode(ElementaryFile ef) {
    byte[] content = ef.getData().getContent(firstRecordNumber);
    if (content.length > 0 && content.length >= getLe()) {
      int length = getLe() != 0 ? getLe() : content.length;
      byte[] apdu = new byte[length + 2];
      System.arraycopy(content, 0, apdu, 0, length); // Record content
      apdu[length] = (byte) 0x90; // SW 9000
      return apdu;
    }
    return null; // NOSONAR
  }

  /**
   * Builds the anticipated APDU response with the SW for multiple records mode.
   *
   * @param ef The EF.
   * @return Null if some records have not been read beforehand.
   */
  private byte[] buildAnticipatedResponseForMultipleRecordsMode(ElementaryFile ef) {
    byte[] apdu = new byte[getLe() + 2];
    int nbRecords = getLe() / (recordSize + 2);
    int lastRecordNumber = firstRecordNumber + nbRecords - 1;
    int index = 0;
    for (int i = firstRecordNumber; i <= lastRecordNumber; i++) {
      byte[] content = ef.getData().getContent(i);
      if (content.length >= recordSize) {
        apdu[index++] = (byte) i; // Record number
        apdu[index++] = (byte) recordSize; // Record size
        System.arraycopy(content, 0, apdu, index, recordSize); // Record content
        index += recordSize;
      } else {
        return null; // NOSONAR
      }
    }
    apdu[index] = (byte) 0x90; // SW 9000
    return apdu;
  }

  /**
   * @return the SFI of the accessed file
   * @since 2.0.1
   */
  int getSfi() {
    return sfi;
  }

  /**
   * @return the number of the first record to read
   * @since 2.0.1
   */
  int getFirstRecordNumber() {
    return firstRecordNumber;
  }

  /**
   * @return the read mode
   * @since 2.0.1
   */
  ReadMode getReadMode() {
    return readMode;
  }
}

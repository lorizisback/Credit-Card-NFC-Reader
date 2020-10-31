package com.pro100svitlo.creditCardNfcReader

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.pro100svitlo.creditCardNfcReader.enums.EmvCardScheme
import com.pro100svitlo.creditCardNfcReader.model.EmvCard
import com.pro100svitlo.creditCardNfcReader.parser.EmvParser
import com.pro100svitlo.creditCardNfcReader.utils.Provider
import java.io.Closeable
import java.io.IOException

class CreditCardReader {

    private lateinit var mProvider: Provider
    private var mException = false
    private lateinit var mInterface: CardNfcInterface
    private var mTag: Tag? = null

    private enum class Tech(val className: String) {
        NFC_A("android.nfc.tech.NfcA"),
        NFC_B("android.nfc.tech.NfcA"),
        UNKNOWN("")
    }

    suspend fun readIntent(tag: Tag, callback: CardNfcInterface, fromStart: Boolean = false) {
        mInterface = callback
        mProvider = Provider()
        mTag = tag
        val tech = parseTech(tag.techList)
        when (tech) {
            Tech.NFC_A, Tech.NFC_B -> handleCard(tech)
            Tech.UNKNOWN -> handleUnknownCard(fromStart)
        }
    }

    private fun handleCard(tech: Tech) {
        mInterface.startNfcReadCard()
        mProvider.log.setLength(0)
        val emvCard = when (tech) {
            Tech.NFC_A -> handleTechACard()
            Tech.NFC_B -> handleTechBCard()
            Tech.UNKNOWN -> null
        }
        emvCard?.let { emvCard ->
                if (emvCard.cardNumber.isNotBlank()) {
                    mInterface.readSuccess(emvCard.cardNumber, emvCard.expiryDate, emvCard.type)
                } else if (emvCard.isNfcLocked) {
                    mInterface.cardWithLockedNfc()
                    mInterface.readFail()
                }
        } ?: {
            mInterface.unknownEmvCard()
            mInterface.readFail()
        }()
    }

    private fun handleTechACard() : EmvCard? {
        val mIsoDep = IsoDep.get(mTag)
        if (mIsoDep == null) {
            mInterface.doNotMoveCardSoFast()
            return null
        }
        mException = false
        return try {
            // Open connection
            mIsoDep.connect()
            mProvider.setmTagCom(mIsoDep)
            val parser = EmvParser(mProvider, true)
            parser.readEmvCard().also { mIsoDep.closeQuietly() }
        } catch (e: IOException) {
            mException = true
            null
        }
    }

    private fun handleTechBCard() : EmvCard? {
        return null
    }

    private fun handleUnknownCard(fromStart: Boolean) {
        if (fromStart) {
            mInterface.unknownEmvCard()
        }
    }

    private fun parseTech(techs: Array<String>): Tech =
            when {
                techs.any { it.contains(Tech.NFC_A.className) } -> Tech.NFC_A
                techs.any { it.contains(Tech.NFC_B.className) } -> Tech.NFC_B
                else -> Tech.UNKNOWN
            }



    fun Closeable.closeQuietly() {
        try {
            close()
        } catch (ioe: IOException) {
            // ignore
        }
    }

}

interface CardNfcInterface {
    fun startNfcReadCard()
    fun doNotMoveCardSoFast()
    fun unknownEmvCard()
    fun cardWithLockedNfc()
    fun readSuccess(cardNumber: String, expireDate: String?, type: EmvCardScheme = EmvCardScheme.UNKNOWN)
    fun readFail()
}
package com.pro100svitlo.nfccardread

import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.pro100svitlo.creditCardNfcReader.CardNfcAsyncTask
import com.pro100svitlo.creditCardNfcReader.CreditCardReader
import com.pro100svitlo.creditCardNfcReader.utils.CardNfcUtils

class MainActivity : AppCompatActivity(), CardNfcAsyncTask.CardNfcInterface {
    private var mCardNfcAsyncTask: CardNfcAsyncTask? = null
    private var mToolbar: MaterialToolbar? = null
    private var mCardReadyContent: LinearLayout? = null
    private var mPutCardContent: TextView? = null
    private var mCardNumberText: TextView? = null
    private var mExpireDateText: TextView? = null
    private var mCardLogoIcon: ImageView? = null
    private var mNfcAdapter: NfcAdapter? = null
    private var mTurnNfcDialog: AlertDialog? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mDoNotMoveCardMessage: String? = null
    private var mUnknownEmvCardMessage: String? = null
    private var mCardWithLockedNfcMessage: String? = null
    private var mIsScanNow = false
    private var mIntentFromCreate = false
    private var mCardNfcUtils: CardNfcUtils? = null
    private var mReader: CreditCardReader? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mToolbar = findViewById<View>(R.id.toolbar) as MaterialToolbar
        setSupportActionBar(mToolbar)
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (mNfcAdapter == null) {
            val noNfc = findViewById<View>(android.R.id.candidatesArea) as TextView
            noNfc.visibility = View.VISIBLE
        } else {
            mCardNfcUtils = CardNfcUtils(this)
            mPutCardContent = findViewById<View>(R.id.content_putCard) as TextView
            mCardReadyContent = findViewById<View>(R.id.content_cardReady) as LinearLayout
            mCardNumberText = findViewById<View>(android.R.id.text1) as TextView
            mExpireDateText = findViewById<View>(android.R.id.text2) as TextView
            mCardLogoIcon = findViewById<View>(android.R.id.icon) as ImageView
            mReader = CreditCardReader()
            createProgressDialog()
            initNfcMessages()
            mIntentFromCreate = true
            onNewIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        mIntentFromCreate = false
        if (mNfcAdapter != null && !mNfcAdapter!!.isEnabled) {
            showTurnOnNfcDialog()
            mPutCardContent!!.visibility = View.GONE
        } else if (mNfcAdapter != null) {
            if (!mIsScanNow) {
                mPutCardContent!!.visibility = View.VISIBLE
                mCardReadyContent!!.visibility = View.GONE
            }
            mCardNfcUtils!!.enableDispatch()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (mNfcAdapter != null) {
            mCardNfcUtils!!.disableDispatch()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (mNfcAdapter != null && mNfcAdapter!!.isEnabled) {
            mCardNfcAsyncTask = CardNfcAsyncTask.Builder(this, intent, mIntentFromCreate).build()
        }
    }

    override fun startNfcReadCard() {
        mIsScanNow = true
        mProgressDialog!!.show()
    }

    override fun cardIsReadyToRead() {
        mPutCardContent!!.visibility = View.GONE
        mCardReadyContent!!.visibility = View.VISIBLE
        var card = mCardNfcAsyncTask!!.cardNumber
        card = getPrettyCardNumber(card)
        val expiredDate = mCardNfcAsyncTask!!.cardExpireDate
        val cardType = mCardNfcAsyncTask!!.cardType
        mCardNumberText!!.text = card
        mExpireDateText!!.text = expiredDate
        parseCardType(cardType)
    }

    override fun doNotMoveCardSoFast() {
        showSnackBar(mDoNotMoveCardMessage)
    }

    override fun unknownEmvCard() {
        showSnackBar(mUnknownEmvCardMessage)
    }

    override fun cardWithLockedNfc() {
        showSnackBar(mCardWithLockedNfcMessage)
    }

    override fun finishNfcReadCard() {
        mProgressDialog!!.dismiss()
        mCardNfcAsyncTask = null
        mIsScanNow = false
    }

    private fun createProgressDialog() {
        val title = getString(R.string.ad_progressBar_title)
        val mess = getString(R.string.ad_progressBar_mess)
        mProgressDialog = ProgressDialog(this)
        mProgressDialog!!.setTitle(title)
        mProgressDialog!!.setMessage(mess)
        mProgressDialog!!.isIndeterminate = true
        mProgressDialog!!.setCancelable(false)
    }

    private fun showSnackBar(message: String?) {
        Snackbar.make(mToolbar!!, message!!, Snackbar.LENGTH_SHORT).show()
    }

    private fun showTurnOnNfcDialog() {
        if (mTurnNfcDialog == null) {
            val title = getString(R.string.ad_nfcTurnOn_title)
            val mess = getString(R.string.ad_nfcTurnOn_message)
            val pos = getString(R.string.ad_nfcTurnOn_pos)
            val neg = getString(R.string.ad_nfcTurnOn_neg)
            mTurnNfcDialog = AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(mess)
                    .setPositiveButton(pos) { dialogInterface, i -> // Send the user to the settings page and hope they turn it on
                        if (Build.VERSION.SDK_INT >= 16) {
                            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                        } else {
                            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        }
                    }
                    .setNegativeButton(neg) { dialogInterface, i -> onBackPressed() }.create()
        }
        mTurnNfcDialog!!.show()
    }

    private fun initNfcMessages() {
        mDoNotMoveCardMessage = getString(R.string.snack_doNotMoveCard)
        mCardWithLockedNfcMessage = getString(R.string.snack_lockedNfcCard)
        mUnknownEmvCardMessage = getString(R.string.snack_unknownEmv)
    }

    private fun parseCardType(cardType: String) {
        if (cardType == CardNfcAsyncTask.CARD_UNKNOWN) {
            Snackbar.make(mToolbar!!, getString(R.string.snack_unknown_bank_card), Snackbar.LENGTH_LONG)
                    .setAction("GO") { goToRepo() }
        } else if (cardType == CardNfcAsyncTask.CARD_VISA) {
            mCardLogoIcon!!.setImageResource(R.mipmap.visa_logo)
        } else if (cardType == CardNfcAsyncTask.CARD_MASTER_CARD) {
            mCardLogoIcon!!.setImageResource(R.mipmap.master_logo)
        }
    }

    private fun getPrettyCardNumber(card: String): String {
        val div = " - "
        return (card.substring(0, 4) + div + card.substring(4, 8) + div + card.substring(8, 12)
                + div + card.substring(12, 16))
    }

    private fun goToRepo() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.repoUrl)))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setPackage("com.android.chrome")
        try {
            startActivity(i)
        } catch (e: ActivityNotFoundException) {
            i.setPackage(null)
            startActivity(i)
        }
    }
}
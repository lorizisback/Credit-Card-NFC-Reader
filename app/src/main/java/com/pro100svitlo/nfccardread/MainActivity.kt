package com.pro100svitlo.nfccardread

import android.app.ProgressDialog
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
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
import com.pro100svitlo.creditCardNfcReader.CardNfcInterface
import com.pro100svitlo.creditCardNfcReader.CreditCardReader
import com.pro100svitlo.creditCardNfcReader.enums.EmvCardScheme
import com.pro100svitlo.creditCardNfcReader.utils.CardNfcUtils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


class MainActivity : AppCompatActivity(), CardNfcInterface, CoroutineScope {
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private var mCardNfcAsyncTask: CardNfcAsyncTask? = null
    private var mToolbar: MaterialToolbar? = null
    private val mCardNumberText: TextView by lazy { findViewById(android.R.id.text1) }
    private val mExpireDateText: TextView by lazy { findViewById(android.R.id.text2) }
    private val mCardLogoIcon: ImageView by lazy { findViewById(android.R.id.icon) }
    private val mNfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var mTurnNfcDialog: AlertDialog? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mIsScanNow = false
    private var mIntentFromCreate = false
    private val mCardNfcUtils: CardNfcUtils by lazy { CardNfcUtils(this) }
    private val mReader: CreditCardReader by lazy { CreditCardReader() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        setContentView(R.layout.activity_main)

        mToolbar = findViewById<View>(R.id.toolbar) as MaterialToolbar
        setSupportActionBar(mToolbar)
        if (mNfcAdapter == null) {
            val noNfc = findViewById<View>(android.R.id.candidatesArea) as TextView
            noNfc.visibility = View.VISIBLE
        } else {
            createProgressDialog()
            mIntentFromCreate = true
            onNewIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        mIntentFromCreate = false
        if (mNfcAdapter != null && !mNfcAdapter!!.isEnabled) {
            showTurnOnNfcDialog()
            content_putCard.visibility = View.GONE
        } else if (mNfcAdapter != null) {
            if (!mIsScanNow) {
                content_putCard.visibility = View.VISIBLE
                content_cardReady.visibility = View.GONE
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
        mNfcAdapter?.let {
            intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)?.let {
                launch {
                    mReader.readIntent(it, this@MainActivity, mIntentFromCreate)
                }
            }
        }
    }

    override fun startNfcReadCard() {
        mIsScanNow = true
        mProgressDialog!!.show()
    }

    override fun doNotMoveCardSoFast() {
        showSnackBar(getString(R.string.snack_doNotMoveCard))
    }

    override fun unknownEmvCard() {
        showSnackBar(getString(R.string.snack_unknownEmv))
    }

    override fun cardWithLockedNfc() {
        showSnackBar(getString(R.string.snack_lockedNfcCard))
    }

    override fun readFail() {
        showSnackBar(getString(R.string.snack_lockedNfcCard))
    }

    override fun readSuccess(cardNumber: String, expireDate: String?, type: EmvCardScheme) {
        mProgressDialog!!.dismiss()
        mIsScanNow = false

        content_putCard.visibility = View.GONE
        content_cardReady.visibility = View.VISIBLE
        mCardNumberText.text = getPrettyCardNumber(cardNumber)
        mExpireDateText.text = expireDate.orEmpty()

        val logo = when (type.toString()) {
            CardNfcAsyncTask.CARD_VISA -> R.mipmap.visa_logo
            CardNfcAsyncTask.CARD_MASTER_CARD -> R.mipmap.master_logo
            else -> -1
        }

        mCardLogoIcon.setImageResource(logo)
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
                    .setPositiveButton(pos) { _, _ -> // Send the user to the settings page and hope they turn it on
                        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    }
                    .setNegativeButton(neg) { dialogInterface, i -> onBackPressed() }.create()
        }
        mTurnNfcDialog!!.show()
    }


    private fun parseCardType(cardType: String) {
        if (cardType == CardNfcAsyncTask.CARD_VISA) {
            mCardLogoIcon.setImageResource(R.mipmap.visa_logo)
        } else if (cardType == CardNfcAsyncTask.CARD_MASTER_CARD) {
            mCardLogoIcon.setImageResource(R.mipmap.master_logo)
        }
    }

    private fun getPrettyCardNumber(card: String): String {
        val div = " - "
        return (card.substring(0, 4) + div + card.substring(4, 8) + div + card.substring(8, 12)
                + div + card.substring(12, 16))
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
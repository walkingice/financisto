package ru.orangesoftware.financisto2.transaction

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.TextView
import android.widget.Toast
import com.mlsdev.rximagepicker.RxImageConverters
import com.mlsdev.rximagepicker.RxImagePicker
import com.mlsdev.rximagepicker.Sources
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import greendroid.widget.QuickActionWidget.OnQuickActionClickListener
import io.reactivex.disposables.CompositeDisposable
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.AbstractActivity
import ru.orangesoftware.financisto.activity.AccountWidget
import ru.orangesoftware.financisto.activity.CategorySelector
import ru.orangesoftware.financisto.activity.CategorySelector.CategorySelectorListener
import ru.orangesoftware.financisto.activity.LocationSelector
import ru.orangesoftware.financisto.activity.MyQuickAction
import ru.orangesoftware.financisto.activity.NotificationOptionsActivity
import ru.orangesoftware.financisto.activity.PayeeSelector
import ru.orangesoftware.financisto.activity.ProjectSelector
import ru.orangesoftware.financisto.activity.RecurrenceActivity
import ru.orangesoftware.financisto.activity.RequestPermission
import ru.orangesoftware.financisto.activity.UiUtils
import ru.orangesoftware.financisto.datetime.DateUtils
import ru.orangesoftware.financisto.db.DatabaseHelper.AccountColumns
import ru.orangesoftware.financisto.db.DatabaseHelper.TransactionColumns
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.MyLocation
import ru.orangesoftware.financisto.model.Project
import ru.orangesoftware.financisto.model.SystemAttribute
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionAttribute
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.recur.NotificationOptions
import ru.orangesoftware.financisto.recur.Recurrence
import ru.orangesoftware.financisto.utils.EnumUtils
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PicturesUtil
import ru.orangesoftware.financisto.utils.TransactionUtils
import ru.orangesoftware.financisto.utils.Utils
import ru.orangesoftware.financisto.view.AttributeView
import ru.orangesoftware.financisto.view.AttributeViewFactory
import ru.orangesoftware.financisto.widget.RateLayoutView
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

abstract class AbstractTransactionActivity : AbstractActivity(), CategorySelectorListener {
    protected lateinit var rateView: RateLayoutView
    protected lateinit var templateName: EditText
    protected lateinit var accountText: TextView
    protected lateinit var accountCursor: Cursor
    protected lateinit var accountAdapter: ListAdapter
    protected lateinit var dateTime: Calendar
    protected lateinit var status: ImageButton
    protected lateinit var dateText: Button
    protected lateinit var timeText: Button
    protected lateinit var noteText: EditText
    protected lateinit var recurText: TextView
    protected lateinit var notificationText: TextView
    private lateinit var pictureView: ImageView
    private lateinit var ccardPayment: CheckBox
    protected var selectedAccount: Account? = null
    protected var recurrenceStr: String? = null
    protected var notificationOptions: String? = null
    protected var isDuplicate = false
    protected var isShowPayee = true
    protected var payeeSelector: PayeeSelector<AbstractTransactionActivity>? = null
    protected var projectSelector: ProjectSelector<AbstractTransactionActivity>? = null
    protected var locationSelector: LocationSelector<AbstractTransactionActivity>? = null
    protected lateinit var categorySelector: CategorySelector<AbstractTransactionActivity>
    protected var isRememberLastAccount = false
    protected var isRememberLastCategory = false
    protected var isRememberLastLocation = false
    protected var isRememberLastProject = false
    protected var isShowNote = false
    protected var isShowTakePicture = false
    protected var isShowIsCCardPayment = false
    protected var isOpenCalculatorForTemplates = false
    protected var deleteAfterExpired: AttributeView? = null
    protected lateinit var df: DateFormat
    protected lateinit var tf: DateFormat
    private lateinit var pickImageActionGrid: QuickActionWidget

    protected var transaction = Transaction()
    protected var disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        df = DateUtils.getLongDateFormat(this)
        tf = DateUtils.getTimeFormat(this)
        val t0 = System.currentTimeMillis()
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        setContentView(getLayoutId())
        isRememberLastAccount = MyPreferences.isRememberAccount(this)
        isRememberLastCategory = isRememberLastAccount && MyPreferences.isRememberCategory(this)
        isRememberLastLocation = isRememberLastCategory && MyPreferences.isRememberLocation(this)
        isRememberLastProject = isRememberLastCategory && MyPreferences.isRememberProject(this)
        isShowNote = MyPreferences.isShowNote(this)
        isShowTakePicture = MyPreferences.isShowTakePicture(this)
        isShowIsCCardPayment = MyPreferences.isShowIsCCardPayment(this)
        isOpenCalculatorForTemplates = MyPreferences.isOpenCalculatorForTemplates(this)
        categorySelector = CategorySelector(this, db, x)
        categorySelector!!.setListener(this)
        fetchCategories()
        var accountId: Long = -1
        var transactionId: Long = -1
        var isNewFromTemplate = false
        val intent = intent
        if (intent != null) {
            accountId = intent.getLongExtra(ACCOUNT_ID_EXTRA, -1)
            transactionId = intent.getLongExtra(TRAN_ID_EXTRA, -1)
            transaction.dateTime = intent.getLongExtra(DATETIME_EXTRA, System.currentTimeMillis())
            if (transactionId != -1L) {
                transaction = db.getTransaction(transactionId)
                transaction.categoryAttributes = db.getAllAttributesForTransaction(transactionId)
                isDuplicate = intent.getBooleanExtra(DUPLICATE_EXTRA, false)
                isNewFromTemplate = intent.getBooleanExtra(NEW_FROM_TEMPLATE_EXTRA, false)
                if (isDuplicate) {
                    transaction.id = -1
                    transaction.dateTime = System.currentTimeMillis()
                }
            }
            transaction.isTemplate = intent.getIntExtra(TEMPLATE_EXTRA, transaction.isTemplate)
        }

        accountCursor = if (transaction.id == -1L) {
            db.allActiveAccounts
        } else {
            db.getAccountsForTransaction(transaction)
        }

        startManagingCursor(accountCursor)
        accountAdapter = TransactionUtils.createAccountAdapter(this, accountCursor)
        dateTime = Calendar.getInstance()
        val date = dateTime.getTime()
        status = findViewById(R.id.status)
        status.setOnClickListener {
            val adapter = EnumUtils.createDropDownAdapter(
                this@AbstractTransactionActivity,
                statuses
            )
            x.selectPosition(
                this@AbstractTransactionActivity,
                R.id.status,
                R.string.transaction_status,
                adapter,
                transaction.status.ordinal
            )
        }
        dateText = findViewById(R.id.date)
        dateText.text = df.format(date)
        dateText.setOnClickListener { arg0: View? ->
            val dialog =
                DatePickerDialog.newInstance(
                    { view: DatePickerDialog?, year: Int, monthOfYear: Int, dayOfMonth: Int ->
                        dateTime.set(year, monthOfYear, dayOfMonth)
                        dateText.setText(df.format(dateTime.getTime()))
                    },
                    dateTime.get(Calendar.YEAR),
                    dateTime.get(Calendar.MONTH),
                    dateTime.get(Calendar.DAY_OF_MONTH)
                )
            UiUtils.applyTheme(this, dialog)
            dialog.show(fragmentManager, "DatePickerDialog")
        }
        timeText = findViewById(R.id.time)
        timeText.text = tf.format(date)
        timeText.setOnClickListener { arg0: View? ->
            val is24Format = DateUtils.is24HourFormat(this@AbstractTransactionActivity)
            val dialog = TimePickerDialog.newInstance(
                { view: TimePickerDialog?, hourOfDay: Int, minute: Int, second: Int ->
                    dateTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    dateTime.set(Calendar.MINUTE, minute)
                    timeText.setText(tf.format(dateTime.getTime()))
                },
                dateTime.get(Calendar.HOUR_OF_DAY),
                dateTime.get(Calendar.MINUTE),
                is24Format
            )
            UiUtils.applyTheme(this, dialog)
            dialog.show(fragmentManager, "TimePickerDialog")
        }
        internalOnCreate()

        val layout = findViewById<LinearLayout>(R.id.list)
        templateName = EditText(this)
        if (transaction.isTemplate()) {
            x.addEditNode(layout, R.string.template_name, templateName)
        }
        rateView = RateLayoutView(this, x, layout)

        locationSelector = LocationSelector(this, db, x)
        locationSelector!!.fetchEntities()
        projectSelector = ProjectSelector(this, db, x)
        projectSelector!!.fetchEntities()
        createListNodes(layout)
        categorySelector!!.createAttributesLayout(layout)
        createCommonNodes(layout)
        if (transaction.isScheduled) {
            recurText = x.addListNode(
                layout,
                R.id.recurrence_pattern,
                R.string.recur,
                R.string.recur_interval_no_recur
            )
            notificationText = x.addListNode(
                layout,
                R.id.notification,
                R.string.notification,
                R.string.notification_options_default
            )
            val sa = db.getSystemAttribute(SystemAttribute.DELETE_AFTER_EXPIRED)
            deleteAfterExpired = AttributeViewFactory.createViewForAttribute(this, sa)
            val value = transaction.getSystemAttribute(SystemAttribute.DELETE_AFTER_EXPIRED)
            deleteAfterExpired!!.inflateView(layout, value ?: sa.defaultValue)
        }
        val bSave = findViewById<Button>(R.id.bSave)
        bSave.setOnClickListener { arg0: View? -> saveAndFinish() }
        val isEdit = transaction.id > 0
        val bSaveAndNew = findViewById<Button>(R.id.bSaveAndNew)
        if (isEdit) {
            bSaveAndNew.setText(R.string.cancel)
        }
        bSaveAndNew.setOnClickListener { arg0: View? ->
            if (isEdit) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                if (saveAndFinish()) {
                    intent!!.putExtra(
                        DATETIME_EXTRA,
                        transaction.dateTime
                    )
                    startActivityForResult(intent, -1)
                }
            }
        }
        if (transactionId != -1L) {
            isOpenCalculatorForTemplates = isOpenCalculatorForTemplates and isNewFromTemplate
            editTransaction(transaction)
        } else {
            setDateTime(transaction.dateTime)
            categorySelector.selectCategory(Category.NO_CATEGORY_ID)
            if (accountId != -1L) {
                selectAccount(accountId)
            } else {
                val lastAccountId = MyPreferences.getLastAccount(this)
                if (isRememberLastAccount && lastAccountId != -1L) {
                    selectAccount(lastAccountId)
                }
            }
            if (!isRememberLastProject) {
                projectSelector!!.selectEntity(Project.NO_PROJECT_ID.toLong())
            }
            if (!isRememberLastLocation) {
                locationSelector!!.selectEntity(MyLocation.CURRENT_LOCATION_ID.toLong())
            }
            if (transaction.isScheduled) {
                selectStatus(TransactionStatus.PN)
            }
        }
        setupPickImageActionGrid()
        val t1 = System.currentTimeMillis()
        Log.i("TransactionActivity", "onCreate " + (t1 - t0) + "ms")
    }

    protected fun setupPickImageActionGrid() {
        pickImageActionGrid = QuickActionGrid(this)
        pickImageActionGrid.addQuickAction(
            MyQuickAction(this, R.drawable.ic_photo_camera, R.string.image_pick_camera)
        )
        pickImageActionGrid.addQuickAction(
            MyQuickAction(this, R.drawable.ic_photo_library, R.string.image_pick_images)
        )
        pickImageActionGrid.setOnQuickActionClickListener { _: QuickActionWidget?, position: Int ->
            when (position) {
                0 -> requestImage(Sources.CAMERA)
                1 -> requestImage(Sources.GALLERY)
            }
        }
    }

    protected fun requestImage(source: Sources?) {
        transaction.blobKey = null
        disposable.add(
            RxImagePicker.with(fragmentManager).requestImage(source)
                .flatMap { uri: Uri? ->
                    RxImageConverters.uriToFile(this, uri, PicturesUtil.createEmptyImageFile())
                }
                .subscribe(
                    { file: File ->
                        selectPicture(
                            file.name
                        )
                    }
                ) { e: Throwable ->
                    Toast.makeText(
                        this@AbstractTransactionActivity,
                        "Unable to pick up an image: " + e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
        )
    }

    protected fun createPayeeNode(layout: LinearLayout?) {
        payeeSelector = PayeeSelector(this, db, x)
        payeeSelector!!.fetchEntities()
        payeeSelector!!.createNode(layout)
    }

    private fun saveAndFinish(): Boolean {
        val id = save()
        if (id > 0) {
            val data = Intent()
            data.putExtra(TransactionColumns._id.name, id)
            setResult(Activity.RESULT_OK, data)
            finish()
            return true
        }
        return false
    }

    private fun save(): Long {
        if (onOKClicked()) {
            val isNew = transaction.id == -1L
            val id = db.insertOrUpdate(transaction, attributes)
            if (isNew) {
                MyPreferences.setLastAccount(this, transaction.fromAccountId)
            }
            AccountWidget.updateWidgets(this)
            return id
        }
        return -1
    }

    private val attributes: List<TransactionAttribute>
        private get() {
            val attributes =
                categorySelector!!.attributes
            if (deleteAfterExpired != null) {
                val ta = deleteAfterExpired!!.newTransactionAttribute()
                attributes.add(ta)
            }
            return attributes
        }

    protected abstract fun internalOnCreate()
    override fun shouldLock(): Boolean {
        return MyPreferences.isPinProtectedNewTransaction(this)
    }

    protected fun createCommonNodes(layout: LinearLayout?) {
        val locationOrder = MyPreferences.getLocationOrder(this)
        val noteOrder = MyPreferences.getNoteOrder(this)
        val projectOrder = MyPreferences.getProjectOrder(this)
        for (i in 0..5) {
            if (i == locationOrder) {
                locationSelector!!.createNode(layout)
            }
            if (i == noteOrder) {
                if (isShowNote) {
                    //note
                    noteText = EditText(this)
                    noteText!!.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    x.addEditNode(layout, R.string.note, noteText)
                }
            }
            if (i == projectOrder) {
                projectSelector!!.createNode(layout)
            }
        }
        if (isShowTakePicture && transaction.isNotTemplateLike) {
            pictureView = x.addPictureNodeMinus(
                this,
                layout,
                R.id.attach_picture,
                R.id.delete_picture,
                R.string.attach_picture,
                R.string.new_picture
            )
        }
        if (isShowIsCCardPayment) {
            // checkbox to register if the transaction is a credit card payment.
            // this will be used to exclude from totals in bill preview
            ccardPayment = x.addCheckboxNode(
                layout, R.id.is_ccard_payment,
                R.string.is_ccard_payment, R.string.is_ccard_payment_summary, false
            )
        }
    }

    protected abstract fun onOKClicked(): Boolean
    override fun onClick(v: View, id: Int) {
        if (isShowPayee) payeeSelector!!.onClick(id)
        projectSelector!!.onClick(id)
        categorySelector!!.onClick(id)
        locationSelector!!.onClick(id)
        when (id) {
            R.id.account -> x.select(
                this, R.id.account, R.string.account, accountCursor, accountAdapter,
                AccountColumns.ID, selectedAccountId
            )
            R.id.recurrence_pattern -> {
                val intent = Intent(this, RecurrenceActivity::class.java)
                intent.putExtra(RecurrenceActivity.RECURRENCE_PATTERN, recurrenceStr)
                startActivityForResult(
                    intent,
                    RECURRENCE_REQUEST
                )
            }
            R.id.notification -> {
                val intent = Intent(this, NotificationOptionsActivity::class.java)
                intent.putExtra(
                    NotificationOptionsActivity.NOTIFICATION_OPTIONS,
                    notificationOptions
                )
                startActivityForResult(
                    intent,
                    NOTIFICATION_REQUEST
                )
            }
            R.id.attach_picture -> {
                if (RequestPermission.isRequestingPermission(
                        this,
                        Manifest.permission.CAMERA
                    )
                ) {
                    return
                }
                transaction.blobKey = null
                pickImageActionGrid!!.show(v)
            }
            R.id.delete_picture -> {
                removePicture()
            }
            R.id.is_ccard_payment -> {
                ccardPayment!!.isChecked = !ccardPayment!!.isChecked
                transaction.isCCardPayment = if (ccardPayment!!.isChecked) 1 else 0
            }
        }
    }

    override fun onSelectedPos(id: Int, selectedPos: Int) {
        if (isShowPayee) payeeSelector!!.onSelectedPos(id, selectedPos)
        projectSelector!!.onSelectedPos(id, selectedPos)
        locationSelector!!.onSelectedPos(id, selectedPos)
        when (id) {
            R.id.status -> selectStatus(
                statuses[selectedPos]
            )
        }
    }

    override fun onSelectedId(id: Int, selectedId: Long) {
        if (isShowPayee) payeeSelector!!.onSelectedId(id, selectedId)
        categorySelector!!.onSelectedId(id, selectedId)
        projectSelector!!.onSelectedId(id, selectedId)
        locationSelector!!.onSelectedId(id, selectedId)
        when (id) {
            R.id.account -> selectAccount(selectedId)
        }
    }

    private fun selectStatus(transactionStatus: TransactionStatus) {
        transaction.status = transactionStatus
        status!!.setImageResource(transactionStatus.iconId)
    }

    protected fun selectAccount(accountId: Long): Account? {
        return selectAccount(accountId, true)
    }

    protected open fun selectAccount(
        accountId: Long,
        selectLast: Boolean
    ): Account? {
        val a = db.getAccount(accountId)
        if (a != null) {
            accountText!!.text = a.title
            rateView!!.selectCurrencyFrom(a.currency)
            selectedAccount = a
        }
        return a
    }

    protected val selectedAccountId: Long
        protected get() = if (selectedAccount != null) selectedAccount!!.id else -1

    override fun onCategorySelected(
        category: Category,
        selectLast: Boolean
    ) {
        addOrRemoveSplits()
        categorySelector!!.addAttributes(transaction)
        switchIncomeExpenseButton(category)
        if (selectLast && isRememberLastLocation) {
            locationSelector!!.selectEntity(category.lastLocationId)
        }
        if (selectLast && isRememberLastProject) {
            projectSelector!!.selectEntity(category.lastProjectId)
        }
        projectSelector!!.setNodeVisible(!category.isSplit)
    }

    protected open fun addOrRemoveSplits() {}
    protected open fun switchIncomeExpenseButton(category: Category) {}
    private fun setRecurrence(recurrence: String?) {
        this.recurrenceStr = recurrence
        if (recurrence == null) {
            recurText.setText(R.string.recur_interval_no_recur)
            dateText.isEnabled = true
            timeText.isEnabled = true
        } else {
            dateText.isEnabled = false
            timeText.isEnabled = false
            val r = Recurrence.parse(recurrence)
            recurText.text = r.toInfoString(this)
        }
    }

    private fun setNotification(notificationOptions: String?) {
        this.notificationOptions = notificationOptions
        if (notificationOptions == null) {
            notificationText.setText(R.string.notification_options_default)
        } else {
            val o = NotificationOptions.parse(notificationOptions)
            notificationText.text = o.toInfoString(this)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        projectSelector!!.onActivityResult(requestCode, resultCode, data)
        categorySelector!!.onActivityResult(requestCode, resultCode, data)
        locationSelector!!.onActivityResult(requestCode, resultCode, data)
        if (isShowPayee) {
            payeeSelector!!.onActivityResult(requestCode, resultCode, data)
        }
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                RECURRENCE_REQUEST -> {
                    val recurrence =
                        data.getStringExtra(RecurrenceActivity.RECURRENCE_PATTERN)
                    setRecurrence(recurrence)
                }
                NOTIFICATION_REQUEST -> {
                    val notificationOptions =
                        data.getStringExtra(NotificationOptionsActivity.NOTIFICATION_OPTIONS)
                    setNotification(notificationOptions)
                }
                else -> {
                }
            }
        } else {
            if (requestCode == PICTURE_REQUEST) {
                removePicture()
            }
        }
    }

    private fun selectPicture(pictureFileName: String?) {
        if (pictureView == null) {
            return
        }
        if (pictureFileName == null) {
            return
        }
        PicturesUtil.showImage(this, pictureView, pictureFileName)
        pictureView!!.setTag(R.id.attached_picture, pictureFileName)
        transaction.attachedPicture = pictureFileName
    }

    private fun removePicture() {
        if (pictureView == null) {
            return
        }
        transaction.attachedPicture = null
        transaction.blobKey = null
        pictureView!!.setImageBitmap(null)
        pictureView!!.setTag(R.id.attached_picture, null)
    }

    protected fun setDateTime(date: Long) {
        val d = Date(date)
        dateTime!!.time = d
        dateText!!.text = df!!.format(d)
        timeText!!.text = tf!!.format(d)
    }

    protected fun commonEditTransaction(transaction: Transaction) {
        selectStatus(transaction.status)
        categorySelector!!.selectCategory(transaction.categoryId, false)
        projectSelector!!.selectEntity(transaction.projectId)
        locationSelector!!.selectEntity(transaction.locationId)
        setDateTime(transaction.dateTime)
        if (isShowNote) {
            noteText!!.setText(transaction.note)
        }
        if (transaction.isTemplate()) {
            templateName!!.setText(transaction.templateName)
        }
        if (transaction.isScheduled) {
            setRecurrence(transaction.recurrence)
            setNotification(transaction.notificationOptions)
        }
        if (isShowTakePicture) {
            selectPicture(transaction.attachedPicture)
        }
        if (isShowIsCCardPayment) {
            setIsCCardPayment(transaction.isCCardPayment)
        }
        if (transaction.isCreatedFromTemlate && isOpenCalculatorForTemplates) {
            rateView!!.openFromAmountCalculator()
        }
    }

    private fun setIsCCardPayment(isCCardPaymentValue: Int) {
        transaction.isCCardPayment = isCCardPaymentValue
        ccardPayment!!.isChecked = isCCardPaymentValue == 1
    }

    protected fun checkSelectedEntities(): Boolean {
        if (isShowPayee) {
            payeeSelector!!.createNewEntity()
        }
        locationSelector!!.createNewEntity()
        projectSelector!!.createNewEntity()
        return true
    }

    protected fun updateTransactionFromUI(transaction: Transaction) {
        transaction.categoryId = categorySelector!!.selectedCategoryId
        transaction.projectId = projectSelector!!.selectedEntityId
        transaction.locationId = locationSelector!!.selectedEntityId
        if (transaction.isScheduled) {
            DateUtils.zeroSeconds(dateTime)
        }
        transaction.dateTime = dateTime!!.time.time
        if (isShowPayee) {
            transaction.payeeId = payeeSelector!!.selectedEntityId
        }
        if (isShowNote) {
            transaction.note = Utils.text(noteText)
        }
        if (transaction.isTemplate()) {
            transaction.templateName = Utils.text(templateName)
        }
        if (transaction.isScheduled) {
            transaction.recurrence = recurrenceStr
            transaction.notificationOptions = notificationOptions
        }
    }

    protected fun selectPayee(payeeId: Long) {
        if (isShowPayee) {
            payeeSelector!!.selectEntity(payeeId)
        }
    }

    override fun onDestroy() {
        disposable.dispose()
        if (categorySelector != null) categorySelector!!.onDestroy()
        super.onDestroy()
    }

    protected abstract fun editTransaction(transaction: Transaction)

    protected abstract fun createListNodes(layout: LinearLayout)

    protected abstract fun getLayoutId(): Int

    protected abstract fun fetchCategories()

    companion object {
        const val TRAN_ID_EXTRA = "tranId"
        const val ACCOUNT_ID_EXTRA = "accountId"
        const val DUPLICATE_EXTRA = "isDuplicate"
        const val TEMPLATE_EXTRA = "isTemplate"
        const val DATETIME_EXTRA = "dateTimeExtra"
        const val NEW_FROM_TEMPLATE_EXTRA = "newFromTemplateExtra"
        private const val RECURRENCE_REQUEST = 4003
        private const val NOTIFICATION_REQUEST = 4004
        private const val PICTURE_REQUEST = 4005
        private val statuses = TransactionStatus.values()
    }
}

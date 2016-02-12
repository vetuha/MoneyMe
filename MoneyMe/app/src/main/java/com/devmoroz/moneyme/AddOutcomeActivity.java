package com.devmoroz.moneyme;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;

import com.afollestad.materialdialogs.MaterialDialog;
import com.devmoroz.moneyme.adapters.CategorySpinnerWithIconsAdapter;
import com.devmoroz.moneyme.helpers.DBHelper;
import com.devmoroz.moneyme.logging.L;
import com.devmoroz.moneyme.models.Account;
import com.devmoroz.moneyme.models.Category;
import com.devmoroz.moneyme.models.CreatedItem;
import com.devmoroz.moneyme.models.Currency;
import com.devmoroz.moneyme.models.Transaction;
import com.devmoroz.moneyme.models.TransactionType;
import com.devmoroz.moneyme.utils.Constants;
import com.devmoroz.moneyme.utils.CurrencyCache;
import com.devmoroz.moneyme.utils.CustomColorTemplate;
import com.devmoroz.moneyme.utils.FormatUtils;
import com.devmoroz.moneyme.utils.datetime.TimeUtils;
import com.devmoroz.moneyme.widgets.CalculatorDialog;
import com.devmoroz.moneyme.widgets.DecimalDigitsInputFilter;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.List;


import static com.devmoroz.moneyme.utils.PhotoUtil.extractImageUrlFromGallery;
import static com.devmoroz.moneyme.utils.PhotoUtil.setImageWithGlide;

public class AddOutcomeActivity extends AppCompatActivity {

    private static final int FROM_GALLERY_REQUEST_CODE = 1;
    private static final int SAVE_REQUEST_CODE = 2;

    private String photoFileName;
    private String photoPath;
    private static Date outcomeDate;
    String defaultCategory;

    private EditText amount;
    private EditText description;
    private FloatingActionButton buttonAdd;
    private TextInputLayout floatingAmountLabel;
    private Button date;
    private Spinner categorySpin;
    private ImageView categoryColor;
    private Spinner accountSpin;
    private RelativeLayout photoWrapper;

    private Toolbar toolbar;
    private ImageView chequeImage;
    private ImageView imageDeletePhoto;


    private DBHelper dbHelper;
    private List<Category> categories;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        defaultCategory = intent.getStringExtra(Constants.OUTCOME_DEFAULT_CATEGORY);
        setTheme(R.style.AppDefaultOutcome);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_outcome);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.fab_grow);

        toolbar = (Toolbar) findViewById(R.id.add_outcome_toolbar);
        initToolbar();

        amount = (EditText) findViewById(R.id.add_outcome_amount);
        amount.setFilters(new InputFilter[]{new DecimalDigitsInputFilter()});
        description = (EditText) findViewById(R.id.add_outcome_note);
        date = (Button) findViewById(R.id.add_outcome_date);
        categorySpin = (Spinner) findViewById(R.id.add_outcome_category);
        categoryColor = (ImageView) findViewById(R.id.add_outcome_category_color);
        accountSpin = (Spinner) findViewById(R.id.add_outcome_account);
        date.setText(TimeUtils.formatShortDate(getApplicationContext(), new Date()));
        outcomeDate = null;
        buttonAdd = (FloatingActionButton) findViewById(R.id.add_outcome_save);
        floatingAmountLabel = (TextInputLayout) findViewById(R.id.text_input_layout_out_amount);
        chequeImage = (ImageView) findViewById(R.id.add_outcome_cheque);
        imageDeletePhoto = (ImageView) findViewById(R.id.delete_outcome_cheque);
        photoWrapper = (RelativeLayout) findViewById(R.id.photoWrapper);
        imageDeletePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removePic();
            }
        });
        buttonAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (amount.getText().toString().isEmpty()) {
                    floatingAmountLabel.setError(getString(R.string.outcome_amount_required));
                    floatingAmountLabel.setErrorEnabled(true);
                    return;
                }
                Intent intent;
                CreatedItem info = new CreatedItem("", "", 0, "");
                try {
                    info = addOutcome();
                } catch (SQLException ex) {
                    L.t(AddOutcomeActivity.this, "Something went wrong.Please,try again.");
                }
                intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.putExtra(Constants.CREATED_ITEM_ID, info.getItemId());
                intent.putExtra(Constants.CREATED_ITEM_CATEGORY, info.getCategory());
                intent.putExtra(Constants.CREATED_ITEM_AMOUNT, info.getAmount());
                intent.putExtra(Constants.CREATED_ITEM_ACCOUNT, info.getAccountId());
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        initCategorySpinner();
        initAccountSpinner();
        setupFloatingLabelError();
        buttonAdd.startAnimation(animation);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_outcome, menu);
        return true;
    }

    private void initToolbar() {
        if (toolbar != null) {
            toolbar.setTitle(R.string.outcome_toolbar_name);
            toolbar.setTitleTextColor(Color.WHITE);
            setSupportActionBar(toolbar);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.inflateMenu(R.menu.menu_outcome);
        }
    }

    private void initCategorySpinner() {
        try {
            dbHelper = MoneyApplication.getInstance().GetDBHelper();
            categories = dbHelper.getCategoryDAO().getSortedCategories();
        }catch(SQLException ex){

        }
        String[] defaults = getResources().getStringArray(R.array.outcome_categories);
        CategorySpinnerWithIconsAdapter adapter = new CategorySpinnerWithIconsAdapter(this, R.layout.category_row, defaults, categories);
        categorySpin.setAdapter(adapter);
        categoryColor.setColorFilter(categories.get(0).getColor());
        if (FormatUtils.isNotEmpty(defaultCategory)) {
            for(Category category: categories){
                if(defaultCategory.equals(category.getTitle())){
                    categoryColor.setColorFilter(category.getColor());
                    categorySpin.setSelection(category.getOrder());
                    break;
                }
            }
        }
        categorySpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position <= CustomColorTemplate.PIECHART_COLORS.length) {
                    categoryColor.setColorFilter(CustomColorTemplate.PIECHART_COLORS[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initAccountSpinner() {
        List<Account> accountList = MoneyApplication.accounts;
        Currency c = CurrencyCache.getCurrencyOrEmpty();
        if (accountList != null) {
            String[] accountsWithBalance = new String[accountList.size()];
            for (int i = 0; i < accountList.size(); i++) {
                Account acc = accountList.get(i);
                accountsWithBalance[i] = FormatUtils.attachAmountToText(acc.getName(), c, acc.getBalance(), false);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, accountsWithBalance);
            accountSpin.setAdapter(adapter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void showDatePickerDialog(View v) {
        DatePickerFragment newFragment = new DatePickerFragment();
        newFragment.setDate(date);
        newFragment.show(getSupportFragmentManager(), "datePicker");
    }

    private CreatedItem addOutcome() throws java.sql.SQLException {
        dbHelper = MoneyApplication.getInstance().GetDBHelper();
        Date dateAdded;
        String outcomeNote = description.getText().toString();
        double outcomeAmount = Double.parseDouble(amount.getText().toString());
        Category selectedCategory = categories.get(categorySpin.getSelectedItemPosition());

        dateAdded = outcomeDate == null ? new Date() : outcomeDate;

        int id = accountSpin.getSelectedItemPosition();
        Account account = dbHelper.getAccountDAO().queryForAll().get(id);
        account.setBalance(account.getBalance() - outcomeAmount);

        Transaction outcome = new Transaction(TransactionType.OUTCOME, outcomeNote, selectedCategory, dateAdded, outcomeAmount, account);
        outcome.setPhoto(photoPath);

        dbHelper.getTransactionDAO().create(outcome);
        dbHelper.getAccountDAO().update(account);

        return new CreatedItem(outcome.getId(), selectedCategory.getTitle(), outcomeAmount, account.getId());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case (android.R.id.home):
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case (R.id.calculator_outcome_toolbar):
                openCalculatorDialog();
                return true;
            case (R.id.image_outcome_toolbar):
                final String[] items = new String[]{getString(R.string.new_picture),
                        getString(R.string.select_from_gallery)};

                new MaterialDialog.Builder(this)
                        .title(R.string.attach_picture)
                        .items(items)
                        .itemsCallback(new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                switch (which) {
                                    case (0):
                                        dialog.dismiss();
                                        takePhoto();
                                        break;
                                    case (1): {
                                        dialog.dismiss();
                                        chosePhotoFromGallery();
                                        break;
                                    }
                                }
                            }
                        })
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void openCalculatorDialog() {
        final MaterialDialog dialog = new MaterialDialog.Builder(AddOutcomeActivity.this)
                .customView(R.layout.dialog_calculator, false).build();
        CalculatorDialog calc = new CalculatorDialog(dialog.getCustomView(), new CalculatorDialog.Callback() {
            @Override
            public void onCloseClick(String result) {
                amount.setText(result);
                amount.setSelection(result.length());
                dialog.dismiss();
            }
        });
        dialog.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == FROM_GALLERY_REQUEST_CODE) {
                photoPath = extractImageUrlFromGallery(this, data);
                L.appendLog("from gallery");
                L.appendLog(photoPath);
            } else if (requestCode == SAVE_REQUEST_CODE) {
                File f = new File(photoPath);
                Uri contentUri = Uri.fromFile(f);
                L.appendLog("take photo");
                L.appendLog(photoPath);
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(contentUri);
                this.sendBroadcast(intent);
            }
            setPic();
        }
    }

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = filename();
            } catch (IOException ex) {
                L.t(getApplicationContext(), "No SD card");
            }
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, SAVE_REQUEST_CODE);
            }
        }
    }

    private void chosePhotoFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.choose_photo)), FROM_GALLERY_REQUEST_CODE);
    }

    private File filename() throws IOException {
        String time = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        photoFileName = time + ".jpg";
        File photoDir =
                new File(Environment.getExternalStorageDirectory() + "/MoneyMe/Receipts");
        if (!photoDir.exists()) {
            photoDir.mkdirs();
        }
        File image = new File(photoDir, photoFileName);
        photoPath = "file:" + image.getAbsolutePath();
        return image;
    }

    private void setPic() {
        if (FormatUtils.isNotEmpty(photoPath)) {
            setImageWithGlide(getApplicationContext(), photoPath, chequeImage);
            photoWrapper.setVisibility(View.VISIBLE);
        }
    }

    private void removePic() {
        if (chequeImage == null) {
            return;
        }
        if (photoPath != null && photoWrapper != null) {
            photoWrapper.setVisibility(View.INVISIBLE);
            photoFileName = null;
            photoPath = null;
            chequeImage.setImageBitmap(null);
        }
    }

    public static class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

        private Button date;

        public void setDate(Button date) {
            this.date = date;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);
            outcomeDate = c.getTime();

            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            outcomeDate = cal.getTime();
            date.setText(TimeUtils.formatShortDate(getContext(), outcomeDate));
        }
    }

    private void setupFloatingLabelError() {
        String currencyName = CurrencyCache.getCurrencyOrEmpty().getName();
        String labelHint = String.format("%s, %s:", getString(R.string.amount), currencyName);
        floatingAmountLabel.setHint(labelHint);
        floatingAmountLabel.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence text, int start, int count, int after) {
                if (text.length() > 0) {
                    floatingAmountLabel.setErrorEnabled(false);
                } else {
                    floatingAmountLabel.setError(getString(R.string.outcome_amount_required));
                    floatingAmountLabel.setErrorEnabled(true);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().length() == 0) {
                    floatingAmountLabel.setError(getString(R.string.outcome_amount_required));
                    floatingAmountLabel.setErrorEnabled(true);
                }
            }
        });
    }

}

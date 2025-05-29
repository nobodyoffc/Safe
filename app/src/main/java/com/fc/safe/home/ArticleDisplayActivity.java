package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ArticleDisplayActivity extends BaseCryptoActivity {
    private static final String TAG = "ArticleDisplayActivity";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_CONTENT = "extra_content";
    public static final String EXTRA_FILE_PATH = "extra_file_path";

    private TextView titleTextView;
    private TextView contentTextView;
    private Button copyButton;
    private Button doneButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViews();
        setupButtons();
        loadContent();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_article_display;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.security_guidelines);
    }

    @Override
    protected void initializeViews() {
        titleTextView = findViewById(R.id.titleTextView);
        contentTextView = findViewById(R.id.contentTextView);
        copyButton = findViewById(R.id.copyButton);
        doneButton = findViewById(R.id.doneButton);
    }

    @Override
    protected void setupButtons() {
        copyButton.setOnClickListener(v -> {
            String title = titleTextView.getText().toString();
            String content = contentTextView.getText().toString();
            String fullText = title + "\n\n" + content;
            copyToClipboard(fullText, "Article Content");
        });

        doneButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this activity
    }

    private void loadContent() {
        Intent intent = getIntent();
        String title = intent.getStringExtra(EXTRA_TITLE);
        String content = intent.getStringExtra(EXTRA_CONTENT);
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);

        if (filePath != null) {
            loadFromFile(filePath);
        } else if (title != null && content != null) {
            displayContent(title, content);
        } else {
            TimberLogger.e(TAG, "No content provided");
            finish();
        }
    }

    private void loadFromFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            TimberLogger.e(TAG, "File does not exist: " + filePath);
            finish();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String title = reader.readLine();
            if (title == null) {
                TimberLogger.e(TAG, "File is empty");
                finish();
                return;
            }

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            if (filePath.endsWith(".md")) {
                displayMarkdownContent(title, content.toString());
            } else {
                displayContent(title, content.toString());
            }
        } catch (IOException e) {
            TimberLogger.e(TAG, "Error reading file: " + e.getMessage());
            finish();
        }
    }

    private void displayContent(String title, String content) {
        titleTextView.setText(title);
        contentTextView.setText(content);
        contentTextView.setLineSpacing(0, 1.2f);
    }

    private void displayMarkdownContent(String title, String content) {
        titleTextView.setText(title);
        
        // Simple markdown formatting
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = content.split("\n");

        for (String line : lines) {
            if (line.startsWith("# ")) {
                // Heading
                SpannableString heading = new SpannableString(line.substring(2));
                heading.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, heading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(heading).append("\n\n");
            } else if (line.startsWith("## ")) {
                // Subheading
                SpannableString subheading = new SpannableString(line.substring(3));
                subheading.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, subheading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(subheading).append("\n\n");
            } else if (line.startsWith("* ") || line.startsWith("- ")) {
                // List item
                builder.append("• ").append(line.substring(2)).append("\n");
            } else if (line.startsWith("**") && line.endsWith("**")) {
                // Bold text
                SpannableString bold = new SpannableString(line.substring(2, line.length() - 2));
                bold.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, bold.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(bold).append("\n");
            } else if (line.startsWith("__") && line.endsWith("__")) {
                // Underlined text
                SpannableString underlined = new SpannableString(line.substring(2, line.length() - 2));
                underlined.setSpan(new UnderlineSpan(), 0, underlined.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(underlined).append("\n");
            } else if (line.trim().isEmpty()) {
                // Empty line - add extra line break for spacing
                builder.append("\n");
            } else {
                builder.append(line).append("\n");
            }
        }
        
        contentTextView.setText(builder);
        contentTextView.setLineSpacing(0, 1.2f);
    }

    public static Intent newIntent(@NonNull android.content.Context context, String title, String content) {
        Intent intent = new Intent(context, ArticleDisplayActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_CONTENT, content);
        return intent;
    }

    public static Intent newIntent(@NonNull android.content.Context context, String filePath) {
        Intent intent = new Intent(context, ArticleDisplayActivity.class);
        intent.putExtra(EXTRA_FILE_PATH, filePath);
        return intent;
    }
} 
package com.company.qa.mobile.android;

import com.company.qa.mobile.BaseScreen;
import io.appium.java_client.android.AndroidDriver;

/**
 * A screen that needs what only Android offers (activities, key events, UiAutomator selectors). One that does not
 * extends {@link BaseScreen}, and stays portable to iOS.
 */
public abstract class AndroidScreen extends BaseScreen {

    protected AndroidScreen(AndroidDriver driver) {
        super(driver);
    }

    @Override
    protected AndroidDriver driver() {
        return (AndroidDriver) super.driver();
    }
}

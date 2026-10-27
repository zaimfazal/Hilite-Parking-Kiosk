package com.hilite.parking.controller;

import com.hilite.parking.Navigator;

/** Implemented by screen controllers that need to switch views. */
public interface NavigatorAware {
    void setNavigator(Navigator navigator);
}

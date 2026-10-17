package com.github.uiautomator.stub;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.github.uiautomator.LithoCompatAccessibilityService;

import java.util.ArrayList;
import java.util.List;

/** A fresh snapshot of the accessibility state used by legacy Litho. */
public class AccessibilityStatus {
    private final boolean enabled;
    private final boolean touchExplorationEnabled;
    private final boolean helperServiceBound;
    private final boolean lithoCompatible;
    private final List<ServiceInfo> services = new ArrayList<>();

    public AccessibilityStatus(Context targetContext) {
        AccessibilityManager manager = (AccessibilityManager) targetContext.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        enabled = manager.isEnabled();
        touchExplorationEnabled = manager.isTouchExplorationEnabled();

        ComponentName helper = new ComponentName(targetContext, LithoCompatAccessibilityService.class);
        boolean helperBound = false;
        boolean applicableService = false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ServiceInfo service = new ServiceInfo(info);
            services.add(service);
            if (service.id != null
                    && helper.equals(ComponentName.unflattenFromString(service.id))) {
                helperBound = true;
            }
            if ((service.eventTypes & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && (service.capabilities & AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT)
                    == AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) {
                applicableService = true;
            }
        }
        helperServiceBound = helperBound;
        lithoCompatible = enabled && (touchExplorationEnabled || applicableService);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTouchExplorationEnabled() {
        return touchExplorationEnabled;
    }

    public boolean isHelperServiceBound() {
        return helperServiceBound;
    }

    public boolean isLithoCompatible() {
        return lithoCompatible;
    }

    public List<ServiceInfo> getServices() {
        return services;
    }

    /** Only the service metadata needed to diagnose the Litho predicate. */
    public static class ServiceInfo {
        private final String id;
        private final int eventTypes;
        private final int capabilities;
        private final int feedbackType;

        private ServiceInfo(AccessibilityServiceInfo info) {
            id = info.getId();
            eventTypes = info.eventTypes;
            capabilities = info.getCapabilities();
            feedbackType = info.feedbackType;
        }

        public String getId() {
            return id;
        }

        public int getEventTypes() {
            return eventTypes;
        }

        public int getCapabilities() {
            return capabilities;
        }

        public int getFeedbackType() {
            return feedbackType;
        }
    }
}

package com.minenash.customhud.HudElements.list;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.minenash.customhud.HudElements.interfaces.HudElement;
import com.minenash.customhud.HudElements.interfaces.MultiElement;
import com.minenash.customhud.HudElements.functional.FunctionalElement;
import com.minenash.customhud.conditionals.Operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ListElement extends FunctionalElement implements HudElement, MultiElement {

    private final HudElement popList, advanceList;

    private final UUID providerID;
    private final ListProvider provider;
    private final List<HudElement> main;
    private final List<HudElement> last;
    private final boolean multiline;
    private final boolean reverse;

    public ListElement(ListProvider provider, UUID providerID, List<HudElement> format, List<HudElement> separator, boolean multiline, boolean reverse) {
        this.provider = provider;
        this.providerID = providerID;
        this.popList = new FunctionalElement.PopList(providerID);
        this.advanceList = new FunctionalElement.AdvanceList(providerID);
        this.multiline = multiline;
        this.reverse = reverse;
        last = format;

        if (format == null)
            main = null;
        else {
            main = new ArrayList<>(format);
            if (separator != null)
                main.addAll(separator);
        }

    }

    public static HudElement of(ListProvider provider, UUID providerID, List<HudElement> format, List<HudElement> separator, Operation operation, boolean reverse) {
        return operation == null ? new ListElement(provider, providerID, format, separator, false, reverse)
                : new FilteredListElement(provider, providerID, format, separator, operation, false, reverse);
    }

    public List<HudElement> expand() {
        if (main == null)
            return Collections.EMPTY_LIST;
        List<?> values = provider.get();
        if (values.isEmpty())
            return Collections.emptyList();
        if (reverse)
            Collections.reverse(values);

        List<HudElement> expanded = new ArrayList<>();
        expanded.add(new FunctionalElement.PushList(providerID, values));

        for (int i = 0; i < values.size(); i++) {
            expanded.addAll(i < values.size() - 1 ? main : last);
            expanded.add(advanceList);
        }

        expanded.set(expanded.size()-1, popList);
        return expanded;
    }

    @Override
    public boolean ignoreNewlineIfEmpty() {
        return !multiline;
    }

    @Override
    public String getString() {
        return expandIntoString();
    }
//
//    @Override
//    public Number getNumber() {
//        return provider.get().size();
//    }
//
//    @Override
//    public boolean getBoolean() {
//        return !provider.get().isEmpty();
//    }

    public static class MultiLineBuilder {
        private static final ListProvider EMPTY = () -> Collections.EMPTY_LIST;

        public final UUID providerID;
        public final ListProvider provider;
        private final List<HudElement> elements = new ArrayList<>();
        private final List<HudElement> separator = new ArrayList<>();
        private final Operation filter;
        private final boolean reverse;
        private boolean separatorMode = false;

        public MultiLineBuilder(ListProvider provider, UUID providerID, Operation filter, boolean reverse) {
            this.provider = provider == null ? EMPTY : provider;
            this.providerID = providerID;
            this.filter = filter;
            this.reverse = reverse;
        }

        public void add(HudElement element) {
            (separatorMode ? separator : elements).add(element);
        }

        public void addAll(List<HudElement> elements) {
            (separatorMode ? separator : this.elements).addAll(elements);
        }

        public void separatorMode() {
            this.separatorMode = true;
        }

        public HudElement build() {
            return filter == null ? new ListElement(provider, providerID, elements, separator, true, reverse)
                    : new FilteredListElement(provider, providerID, elements, separator, filter, true, reverse);
        }

    }



}

package com.quanglewangle.peter.cashflow.data;

import java.util.ArrayList;
import java.util.List;

public class AddCheckpointResult {
    public long id;
    // One-off entries already tagged to this card for the payment period the
    // new checkpoint anchors -- likely redundant now (e.g. a sundries buffer
    // standing in for spending a fresh, verified balance now covers).
    public List<EntryEntity> existingOneOffs = new ArrayList<>();
}

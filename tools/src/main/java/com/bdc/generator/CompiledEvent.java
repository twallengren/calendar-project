package com.bdc.generator;

import com.bdc.model.Event;
import com.bdc.model.EventProvenance;

/** One occurrence, preserving provenance even when multiple published event rows are identical. */
public record CompiledEvent(Event event, EventProvenance provenance) {}

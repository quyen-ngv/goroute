package com.ds.goroute.annotations;

import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a request field as user-entered text that must go through the shared content
 * filter (MOD-03).
 *
 * <p>The alternative -- calling the filter by hand in every service -- was rejected for
 * one reason: it will be forgotten. The twentieth feature that adds a text field will not
 * remember to call it, and the result is a filter that blocks a word in reviews and lets
 * it through in trip descriptions. Annotating the field turns "remember to call the
 * filter" into "remember to mark a field", which is visible in review and can be checked
 * automatically (see {@code ModeratedFieldCoverageTest}).
 *
 * <p>Because create and update requests both carry annotated fields, editing goes through
 * the same filter as creating; there is no edit-shaped way around it.
 *
 * <p>Applies to {@code String} fields and to collections of strings. Nested request
 * objects are traversed automatically, so an annotation is only needed on the leaf.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModeratedText {

    /**
     * How many people can see this text. Strictness follows from it (MOD-07): public
     * content gets both filter layers, group content only the term list, one-to-one
     * messages are not filtered proactively at all.
     */
    ModerationVisibility visibility() default ModerationVisibility.PUBLIC;

    /** Content kind, recorded on the decision so metrics can be sliced by feature. */
    ModeratedContentType contentType();

    /**
     * Field name recorded with the decision, for example {@code text} or {@code title}.
     * Defaults to the declared field name.
     */
    String label() default "";
}

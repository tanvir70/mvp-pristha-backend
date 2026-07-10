package com.prishtha.mvp.studio.internal.util.constant;

public final class StudioRouteConstant {

    private StudioRouteConstant() {}

    public static final String AUTHOR_POSTS_BASE_PATH = "/api/v1/author/posts";
    public static final String BY_ID = "/{writingId}";
    public static final String PUBLISH = "/{writingId}/publish";
    public static final String UNPUBLISH = "/{writingId}/unpublish";
    public static final String CATEGORIES = "/{writingId}/tags";
}

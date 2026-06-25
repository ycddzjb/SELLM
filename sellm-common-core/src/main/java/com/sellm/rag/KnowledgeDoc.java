package com.sellm.rag;

public class KnowledgeDoc {
    private final String docId;
    private final String category;   // SCALE_SYSTEM / IEP_CASE / POLICY_ETHICS;可空
    private final String content;
    private final String source;

    public KnowledgeDoc(String docId, String content, String source) {
        this(docId, null, content, source);
    }

    public KnowledgeDoc(String docId, String category, String content, String source) {
        this.docId = docId;
        this.category = category;
        this.content = content;
        this.source = source;
    }

    public String getDocId() { return docId; }
    public String getCategory() { return category; }
    public String getContent() { return content; }
    public String getSource() { return source; }
}

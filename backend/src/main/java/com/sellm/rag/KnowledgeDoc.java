package com.sellm.rag;

public class KnowledgeDoc {
    private final String docId;
    private final String content;
    private final String source;

    public KnowledgeDoc(String docId, String content, String source) {
        this.docId = docId;
        this.content = content;
        this.source = source;
    }

    public String getDocId() { return docId; }
    public String getContent() { return content; }
    public String getSource() { return source; }
}

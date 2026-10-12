package com.musabgpt.m5;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class CodeArtifactsTest {
    @Test public void extractsWebsiteFromFencedFreeModelReply(){
        String fence=new String(new char[]{96,96,96});
        String text="Here is your project:\n"+fence+"html\n"
            +"<!doctype html><html><head><title>Calculator</title></head><body><h1>Working</h1></body></html>"
            +"\n"+fence+"\n"+fence+"css\nbody { color: blue; padding: 2rem; }\n"+fence
            +"\n"+fence+"javascript\nconst x = 1 + 2; console.log(x);\n"+fence;
        ArrayList<CodeArtifacts.Artifact> result=CodeArtifacts.extract(text);
        assertEquals(3,result.size());
        assertEquals("index.html",result.get(0).path);
        assertEquals("style.css",result.get(1).path);
        assertEquals("app.js",result.get(2).path);
        assertTrue(result.get(2).content.contains("console.log"));
    }
    @Test public void acceptsCompleteStandaloneHtml(){
        String html="<!doctype html><html lang=\"en\"><head><title>Test</title></head><body><h1>Useful page</h1></body></html>";
        ArrayList<CodeArtifacts.Artifact> result=CodeArtifacts.extract(html);
        assertEquals(1,result.size());
        assertEquals("index.html",result.get(0).path);
    }
    @Test public void rejectsConversationalAnswersAndAmbiguousCopies(){
        String fence=new String(new char[]{96,96,96});
        assertTrue(CodeArtifacts.extract("I cannot install apps on your phone.").isEmpty());
        assertTrue(CodeArtifacts.extract(fence+"bash\nrm -rf /\n"+fence).isEmpty());
        assertTrue(CodeArtifacts.extract(
            fence+"js\nconsole.log('first file');\n"+fence+"\n"
           +fence+"javascript\nconsole.log('second file');\n"+fence).isEmpty());
    }
    @Test public void refusesLargeModelOutput(){
        assertTrue(CodeArtifacts.extract("x".repeat(76000)).isEmpty());
    }
}

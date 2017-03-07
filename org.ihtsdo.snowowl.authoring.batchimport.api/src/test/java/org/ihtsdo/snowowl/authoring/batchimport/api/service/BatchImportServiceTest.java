package org.ihtsdo.snowowl.authoring.batchimport.api.service;

import java.util.List;

import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch.BatchImportExpression;
import org.ihtsdo.snowowl.authoring.batchimport.api.service.BatchImportService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationship;

public class BatchImportServiceTest {
	
	@Autowired
	BatchImportService service = new BatchImportService();

	@Test
	public void test() throws ProcessingException {
		String testExpression = "=== 64572001 | Disease |: { 363698007 | Finding site | = 38848004 | Duodenal structure , 116676008 | Associated morphology | = 24551003 | Arteriovenous malformation , 246454002 | Occurrence | = 255399007 | Congenital} ";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		List<ISnomedBrowserRelationship> rel = service.convertExpressionToRelationships("NEW_SCTID", exp);
		//Parent + 3 defining attributes = 4 relationships
		Assert.assertTrue(rel.size() == 4);
	}

}

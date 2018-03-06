/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.businessproduction.service;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.app.production.db.IManufOrder;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.ProductVariantService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.ProdProcess;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.service.ManufOrderServiceImpl;
import com.axelor.apps.production.service.ManufOrderWorkflowService;
import com.axelor.apps.production.service.OperationOrderService;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class ManufOrderServiceBusinessImpl extends ManufOrderServiceImpl  {
	
	private final Logger logger = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	protected OperationOrderServiceBusinessImpl operationOrderServiceBusinessImpl;

	@Inject
	public ManufOrderServiceBusinessImpl(SequenceService sequenceService, OperationOrderService operationOrderService,
			ManufOrderWorkflowService manufOrderWorkflowService, ProductVariantService productVariantService,
			AppProductionService appProductionService, ManufOrderRepository manufOrderRepo,
			OperationOrderServiceBusinessImpl operationOrderServiceBusinessImpl) {
		super(sequenceService, operationOrderService, manufOrderWorkflowService, productVariantService, appProductionService,
				manufOrderRepo);
		this.operationOrderServiceBusinessImpl = operationOrderServiceBusinessImpl;
		
	}


	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void propagateIsToInvoice(ManufOrder manufOrder) {

		logger.debug("{} is to invoice ? {}", manufOrder.getManufOrderSeq(), manufOrder.getIsToInvoice());
		
		boolean isToInvoice = manufOrder.getIsToInvoice();
		
		if(manufOrder.getOperationOrderList() != null)  {
			for(OperationOrder operationOrder : manufOrder.getOperationOrderList())  {
				
				operationOrder.setIsToInvoice(isToInvoice);
				
			}
		}
		
		manufOrderRepo.save(manufOrder);
		
	}

	
	
	@Override
	public ManufOrder createManufOrder(Product product, BigDecimal qty, int priority, boolean isToInvoice, Company company,
			BillOfMaterial billOfMaterial, LocalDateTime plannedStartDateT) throws AxelorException  {
		

		ManufOrder manufOrder = super.createManufOrder(product, qty, priority, isToInvoice, company, billOfMaterial, plannedStartDateT);
		
		manufOrder.setIsToInvoice(isToInvoice);
			

		return manufOrder; 
		
	}
	
}
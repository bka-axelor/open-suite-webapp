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
package com.axelor.apps.bankpayment.service.bankorder;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.db.BankOrderLine;
import com.axelor.apps.bankpayment.db.repo.BankOrderRepository;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.beust.jcommander.internal.Lists;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class BankOrderMergeServiceImpl implements BankOrderMergeService  {
	
	private final Logger log = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	protected BankOrderRepository bankOrderRepo;
	protected InvoicePaymentRepository invoicePaymentRepo;
	protected BankOrderService bankOrderService;

	
	@Inject
	public BankOrderMergeServiceImpl(BankOrderRepository bankOrderRepo, InvoicePaymentRepository invoicePaymentRepo, 
			BankOrderService bankOrderService)  {
		
		this.bankOrderRepo = bankOrderRepo;
		this.invoicePaymentRepo = invoicePaymentRepo;
		this.bankOrderService = bankOrderService;
		
	}
	
	@Transactional
	public BankOrder mergeBankOrders(Collection<BankOrder> bankOrders) throws AxelorException   {
		
		
		if (bankOrders == null || bankOrders.size() <= 1) {  
			throw new AxelorException(IException.INCONSISTENCY, IExceptionMessage.BANK_ORDER_MERGE_AT_LEAST_TWO_BANK_ORDERS);
		}

		this.checkSameElements(bankOrders);
		
		BankOrder bankOrder = bankOrders.iterator().next();
		
		bankOrders.remove(bankOrder);
		
		for(BankOrderLine bankOrderLine : this.getAllBankOrderLineList(bankOrders))  {
			
			bankOrder.addBankOrderLineListItem(bankOrderLine);
			
		}
			
		for(BankOrder bankOrderToRemove : bankOrders)  {
			
			List<InvoicePayment> invoicePaymentList = invoicePaymentRepo.findByBankOrder(bankOrderToRemove).fetch();
			
			for(InvoicePayment invoicePayment : invoicePaymentList)  {
				
				invoicePayment.setBankOrder(bankOrder);
				
			}
			
			bankOrderRepo.remove(bankOrderToRemove);
			
		}
		
		bankOrderService.updateTotalAmounts(bankOrder);
		
		return bankOrderRepo.save(bankOrder);
		
	}

	
	protected void checkSameElements(Collection<BankOrder> bankOrders) throws AxelorException  {
		
		BankOrder refBankOrder = bankOrders.iterator().next();
		
		int refStatusSelect = refBankOrder.getStatusSelect();
		int orderTypeSelect = refBankOrder.getOrderTypeSelect();
		PaymentMode refPaymentMode = refBankOrder.getPaymentMode();
		int refPartnerTypeSelect = refBankOrder.getPartnerTypeSelect();
		Company refSenderCompany = refBankOrder.getSenderCompany();
		BankDetails refSenderBankDetails = refBankOrder.getSenderBankDetails();
		Currency refCurrency = refBankOrder.getBankOrderCurrency();
		boolean isMultiCurrency = refBankOrder.getIsMultiCurrency();
		
		for(BankOrder bankOrder : bankOrders)  {
			
			int statusSelect = bankOrder.getStatusSelect();
			if (statusSelect != BankOrderRepository.STATUS_DRAFT && statusSelect != BankOrderRepository.STATUS_AWAITING_SIGNATURE) {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_STATUS));
			}
			
			if (statusSelect != refStatusSelect) {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_STATUS));
			}
			
			if (!bankOrder.getOrderTypeSelect().equals(orderTypeSelect)) {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_ORDER_TYPE_SELECT));
			}
			
			if (!bankOrder.getPaymentMode().equals(refPaymentMode)) {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_PAYMENT_MODE));
			}
			
			if (!bankOrder.getPartnerTypeSelect().equals(refPartnerTypeSelect)) {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_PARTNER_TYPE_SELECT));
			}
						
			if (!bankOrder.getSenderCompany().equals(refSenderCompany)) {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_SENDER_COMPANY));
			}
			
			if(!bankOrder.getSenderBankDetails().equals(refSenderBankDetails))  {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_SENDER_BANK_DETAILS));
			}
			
			if (bankOrder.getIsMultiCurrency() != isMultiCurrency || !bankOrder.getIsMultiCurrency() && !bankOrder.getBankOrderCurrency().equals(refCurrency)) {
				throw new AxelorException(bankOrder, IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_SAME_CURRENCY));
			}
			
		}
		
		
	}
	
	
	protected List<BankOrderLine> getAllBankOrderLineList(Collection<BankOrder> bankOrders)  {
		
		List<BankOrderLine> bankOrderLineList = Lists.newArrayList();
		
		for(BankOrder bankOrder : bankOrders)  {
			
			bankOrderLineList.addAll(bankOrder.getBankOrderLineList());
			
		}
		
		return bankOrderLineList;
		
	}

	@Override
	@Transactional(rollbackOn = { AxelorException.class, Exception.class })
	public BankOrder mergeFromInvoicePayments(Collection<InvoicePayment> invoicePayments) throws AxelorException {

		if (invoicePayments == null || invoicePayments.isEmpty()) {
			throw new AxelorException(IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_NO_BANK_ORDERS));
		}

		Collection<InvoicePayment> invoicePaymentsWithBankOrders = new ArrayList<>();
		Collection<BankOrder> bankOrders = new LinkedHashSet<>();

        for (InvoicePayment invoicePayment : invoicePayments) {
            BankOrder bankOrder = invoicePayment.getBankOrder();

            if (bankOrder != null) {
                invoicePaymentsWithBankOrders.add(invoicePayment);
                bankOrders.add(bankOrder);
            }
        }

		if (bankOrders.size() > 1) {
			BankOrder mergedBankOrder = mergeBankOrders(bankOrders);

			for (InvoicePayment invoicePayment : invoicePaymentsWithBankOrders) {
				invoicePayment.setBankOrder(mergedBankOrder);
			}

			return mergedBankOrder;
		}

		if (!bankOrders.isEmpty()) {
			return bankOrders.iterator().next();
		}

		throw new AxelorException(IException.INCONSISTENCY, I18n.get(IExceptionMessage.BANK_ORDER_MERGE_NO_BANK_ORDERS));
	}

}
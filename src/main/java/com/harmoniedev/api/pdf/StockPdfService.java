package com.harmoniedev.api.pdf;

import com.harmoniedev.api.stock.domain.dto.response.StockDashboardItemResponse;
import com.harmoniedev.api.stock.domain.dto.response.StockDashboardResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class StockPdfService {
	private static final Color NAVY = new Color(11, 46, 115);
	private static final Color MUTED = new Color(90, 100, 120);
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public byte[] generateStockReportPdf(StockDashboardResponse dashboard) {
		Document document = new Document(PageSize.A4, 32, 32, 40, 40);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			PdfWriter.getInstance(document, out);
			document.open();

			document.add(new Paragraph("Rapport de stock", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, NAVY)));
			document.add(new Paragraph(
					"Généré le " + LocalDate.now().format(DATE_FMT), FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED)));
			document.add(new Paragraph(" "));

			Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED);
			Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.DARK_GRAY);
			PdfPTable totals = new PdfPTable(2);
			totals.setWidthPercentage(60);
			totals.setSpacingBefore(4);
			addStat(totals, "Valeur totale du stock (HT)", money(dashboard.getTotalValueHT()), labelFont, valueFont);
			addStat(totals, "Valeur totale du stock (TTC)", money(dashboard.getTotalValueTTC()), labelFont, valueFont);
			document.add(totals);
			document.add(new Paragraph(" "));

			document.add(new Paragraph("Détail par produit", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, NAVY)));
			document.add(buildItemsTable(dashboard.getItems()));

			document.close();
			return out.toByteArray();
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to generate stock report PDF", ex);
		}
	}

	private PdfPTable buildItemsTable(java.util.List<StockDashboardItemResponse> items) {
		PdfPTable table = new PdfPTable(new float[] {2.6f, 1.2f, 1, 1.3f, 1.3f, 1.3f, 1.3f});
		table.setWidthPercentage(100);
		table.setSpacingBefore(8);
		Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
		Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
		for (String header : new String[] {"Article", "Réf", "Qté", "PU HT", "Total HT", "PU TTC", "Total TTC"}) {
			PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
			cell.setBackgroundColor(NAVY);
			cell.setPadding(6);
			table.addCell(cell);
		}
		if (items.isEmpty()) {
			PdfPCell empty = new PdfPCell(new Phrase("Aucun article en stock.", rowFont));
			empty.setColspan(7);
			empty.setPadding(8);
			table.addCell(empty);
			return table;
		}
		for (StockDashboardItemResponse item : items) {
			addCell(table, item.getArticle(), rowFont, Element.ALIGN_LEFT);
			addCell(table, item.getRef() == null ? "—" : item.getRef(), rowFont, Element.ALIGN_LEFT);
			addCell(table, String.valueOf(item.getQuantite()), rowFont, Element.ALIGN_RIGHT);
			addCell(table, money(item.getPuHT()), rowFont, Element.ALIGN_RIGHT);
			addCell(table, money(item.getTotalHT()), rowFont, Element.ALIGN_RIGHT);
			addCell(table, money(item.getPuTTC()), rowFont, Element.ALIGN_RIGHT);
			addCell(table, money(item.getTotalTTC()), rowFont, Element.ALIGN_RIGHT);
		}
		return table;
	}

	private void addCell(PdfPTable table, String text, Font font, int align) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font));
		cell.setPadding(6);
		cell.setHorizontalAlignment(align);
		table.addCell(cell);
	}

	private void addStat(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(com.lowagie.text.Rectangle.BOX);
		cell.setPadding(10);
		cell.addElement(new Paragraph(label, labelFont));
		cell.addElement(new Paragraph(value, valueFont));
		table.addCell(cell);
	}

	private String money(double amount) {
		return String.format(Locale.ROOT, "%.3f TND", amount);
	}
}

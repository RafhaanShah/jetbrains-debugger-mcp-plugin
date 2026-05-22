package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.settings

import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.McpConstants
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.KtorMcpServer
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.McpServerService
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation.CustomEvaluateExpressionBlockRule
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation.CustomEvaluateExpressionBlockRuleValidator
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation.EvaluateExpressionSafetyMode
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import java.awt.Dimension
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class McpSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private var serverHostComboBox: JComboBox<String>? = null
    private var maxHistorySpinner: JSpinner? = null
    private var serverPortSpinner: JSpinner? = null
    private var safetyModeComboBox: JComboBox<EvaluateExpressionSafetyMode>? = null
    private var safetyModeExplanationLabel: JBLabel? = null
    private var customRegexTable: TableView<CustomEvaluateExpressionBlockRule>? = null
    private var customRegexTableModel: ListTableModel<CustomEvaluateExpressionBlockRule>? = null

    override fun getDisplayName(): String = McpConstants.SETTINGS_DISPLAY_NAME

    override fun createComponent(): JComponent {
        serverHostComboBox = JComboBox<String>(arrayOf("127.0.0.1", "0.0.0.0")).apply {
            isEditable = true
            toolTipText = "The bind address for the MCP server. Use 127.0.0.1 for localhost only, 0.0.0.0 for all interfaces, or enter a custom IP."
        }
        serverPortSpinner = JSpinner(SpinnerNumberModel(McpConstants.getDefaultServerPort(), 1024, 65535, 1)).apply {
            toolTipText = "The port number for the MCP server (1024-65535). Different IDEs have different defaults to avoid conflicts."
        }
        maxHistorySpinner = JSpinner(SpinnerNumberModel(1000, 100, 10000, 100))
        safetyModeComboBox = JComboBox(EvaluateExpressionSafetyMode.entries.toTypedArray()).apply {
            toolTipText = "Controls plugin-side filtering before evaluate_expression reaches the IDE debugger evaluator."
            addActionListener { updateSafetyModeExplanation() }
        }
        safetyModeExplanationLabel = JBLabel()
        customRegexTableModel = ListTableModel(createCustomRegexColumns(), mutableListOf())
        customRegexTable = TableView(customRegexTableModel!!).apply {
            emptyText.text = "No additional regex rules"
            tableHeader.reorderingAllowed = false
            setMinRowHeight(24)
        }

        val customRegexPanel = ToolbarDecorator
            .createDecorator(customRegexTable!!)
            .setAddActionName("Add regex")
            .setRemoveActionName("Remove regex")
            .setMoveUpActionName("Move up")
            .setMoveDownActionName("Move down")
            .setAddAction {
                addCustomRegexRule()
            }
            .setRemoveAction {
                removeSelectedCustomRegexRules()
            }
            .setMoveUpAction {
                moveSelectedCustomRegexRule(-1)
            }
            .setMoveDownAction {
                moveSelectedCustomRegexRule(1)
            }
            .setPreferredSize(Dimension(760, 150))
            .createPanel()

        updateSafetyModeExplanation()

        mainPanel = FormBuilder.createFormBuilder()
            .addSeparator()
            .addLabeledComponent(JBLabel("Server host:"), serverHostComboBox!!, 1, false)
            .addLabeledComponent(JBLabel("Server port:"), serverPortSpinner!!, 1, false)
            .addLabeledComponent(JBLabel("Max history size:"), maxHistorySpinner!!, 1, false)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Evaluate Expression Safety</b></html>"))
            .addLabeledComponent(JBLabel("Safety mode:"), safetyModeComboBox!!, 1, false)
            .addComponentToRightColumn(safetyModeExplanationLabel!!, 1)
            .addComponentToRightColumn(JBLabel(RESTRICTED_CATEGORIES_HTML), 1)
            .addComponentToRightColumn(JBLabel(CUSTOM_REGEX_HELP_HTML), 1)
            .addLabeledComponent(JBLabel("Additional blocked regex patterns:"), customRegexPanel, 1, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = McpSettings.getInstance()
        return (serverHostComboBox?.selectedItem as? String ?: McpConstants.DEFAULT_SERVER_HOST) != settings.serverHost ||
               (serverPortSpinner?.value as? Int) != settings.serverPort ||
               (maxHistorySpinner?.value as? Int) != settings.maxHistorySize ||
               (safetyModeComboBox?.selectedItem as? EvaluateExpressionSafetyMode ?: EvaluateExpressionSafetyMode.DEFAULT) != settings.evaluateExpressionSafetyMode ||
               getCustomRegexRulesFromTable() != settings.customEvaluateExpressionBlockRules
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val settings = McpSettings.getInstance()
        val oldPort = settings.serverPort
        val oldHost = settings.serverHost
        val newPort = serverPortSpinner?.value as? Int ?: McpConstants.getDefaultServerPort()
        val newHost = (serverHostComboBox?.selectedItem as? String)?.trim() ?: McpConstants.DEFAULT_SERVER_HOST
        val newSafetyMode = safetyModeComboBox?.selectedItem as? EvaluateExpressionSafetyMode
            ?: EvaluateExpressionSafetyMode.DEFAULT
        val newCustomRegexRules = getCustomRegexRulesFromTable()

        CustomEvaluateExpressionBlockRuleValidator.findValidationError(newCustomRegexRules)?.let { error ->
            throw ConfigurationException(error.message, "Invalid Evaluate Expression Regex")
        }

        // Validate port availability before applying (only if port or host changed)
        if ((newPort != oldPort || newHost != oldHost) && !isPortAvailable(newPort, newHost)) {
            throw ConfigurationException(
                "Port $newPort is already in use on $newHost. Please choose a different port or host.",
                "Port Unavailable"
            )
        }

        settings.serverPort = newPort
        settings.serverHost = newHost
        settings.maxHistorySize = (maxHistorySpinner?.value as? Int) ?: 1000
        settings.evaluateExpressionSafetyMode = newSafetyMode
        settings.customEvaluateExpressionBlockRules = newCustomRegexRules

        // Auto-restart server if port or host changed
        if (newPort != oldPort || newHost != oldHost) {
            ApplicationManager.getApplication().invokeLater {
                val result = McpServerService.getInstance().restartServer(newPort, newHost)
                when (result) {
                    is KtorMcpServer.StartResult.Success -> {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup(McpConstants.NOTIFICATION_GROUP_ID)
                            .createNotification(
                                "MCP Server Restarted",
                                "Server is now running on $newHost:$newPort",
                                NotificationType.INFORMATION
                            )
                            .notify(null)
                    }
                    is KtorMcpServer.StartResult.PortInUse -> {
                        // This shouldn't happen since we validated above, but handle it anyway
                    }
                    is KtorMcpServer.StartResult.Error -> {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup(McpConstants.NOTIFICATION_GROUP_ID)
                            .createNotification(
                                "MCP Server Error",
                                result.message,
                                NotificationType.ERROR
                            )
                            .notify(null)
                    }
                }
            }
        }
    }

    /**
     * Checks if a port is available for binding.
     * Returns true if we can bind to the port, false if it's in use.
     */
    private fun isPortAvailable(port: Int, host: String = McpSettings.getInstance().serverHost): Boolean {
        val settings = McpSettings.getInstance()
        val currentPort = settings.serverPort
        val currentHost = settings.serverHost
        if (port == currentPort && host == currentHost && McpServerService.getInstance().isServerRunning()) {
            return true
        }

        return try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(host, port))
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun reset() {
        val settings = McpSettings.getInstance()
        serverHostComboBox?.selectedItem = settings.serverHost
        serverPortSpinner?.value = settings.serverPort
        maxHistorySpinner?.value = settings.maxHistorySize
        safetyModeComboBox?.selectedItem = settings.evaluateExpressionSafetyMode
        customRegexTableModel?.setItems(settings.customEvaluateExpressionBlockRules)
        updateSafetyModeExplanation()
    }

    override fun disposeUIResources() {
        mainPanel = null
        serverHostComboBox = null
        serverPortSpinner = null
        maxHistorySpinner = null
        safetyModeComboBox = null
        safetyModeExplanationLabel = null
        customRegexTable = null
        customRegexTableModel = null
    }

    private fun createCustomRegexColumns(): Array<ColumnInfo<CustomEvaluateExpressionBlockRule, *>> {
        return arrayOf(
            object : ColumnInfo<CustomEvaluateExpressionBlockRule, Boolean>("Enabled") {
                override fun valueOf(item: CustomEvaluateExpressionBlockRule): Boolean = item.enabled

                override fun isCellEditable(item: CustomEvaluateExpressionBlockRule): Boolean = true

                override fun setValue(item: CustomEvaluateExpressionBlockRule, value: Boolean?) {
                    item.enabled = value == true
                }

                override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java

                override fun getPreferredStringValue(): String = "Enabled"
            },
            object : ColumnInfo<CustomEvaluateExpressionBlockRule, String>("Regex") {
                override fun valueOf(item: CustomEvaluateExpressionBlockRule): String = item.pattern

                override fun isCellEditable(item: CustomEvaluateExpressionBlockRule): Boolean = true

                override fun setValue(item: CustomEvaluateExpressionBlockRule, value: String?) {
                    item.pattern = value.orEmpty()
                }

                override fun getPreferredStringValue(): String = "com\\.company\\.SecretStore"
            },
            object : ColumnInfo<CustomEvaluateExpressionBlockRule, String>("Reason shown when blocked") {
                override fun valueOf(item: CustomEvaluateExpressionBlockRule): String = item.reason

                override fun isCellEditable(item: CustomEvaluateExpressionBlockRule): Boolean = true

                override fun setValue(item: CustomEvaluateExpressionBlockRule, value: String?) {
                    item.reason = value.orEmpty()
                }

                override fun getPreferredStringValue(): String = "Internal policy"
            }
        )
    }

    private fun updateSafetyModeExplanation() {
        val mode = safetyModeComboBox?.selectedItem as? EvaluateExpressionSafetyMode
            ?: EvaluateExpressionSafetyMode.DEFAULT
        safetyModeExplanationLabel?.text = modeExplanationHtml(mode)
    }

    private fun addCustomRegexRule() {
        val table = customRegexTable ?: return
        val model = customRegexTableModel ?: return
        table.stopEditing()
        model.addRow(CustomEvaluateExpressionBlockRule())
        selectModelRow(model.rowCount - 1)
    }

    private fun removeSelectedCustomRegexRules() {
        val table = customRegexTable ?: return
        val model = customRegexTableModel ?: return
        table.stopEditing()
        table.selectedRows
            .map { table.convertRowIndexToModel(it) }
            .sortedDescending()
            .forEach { model.removeRow(it) }
    }

    private fun moveSelectedCustomRegexRule(direction: Int) {
        val table = customRegexTable ?: return
        val model = customRegexTableModel ?: return
        table.stopEditing()
        val row = table.selectedRow.takeIf { it >= 0 }?.let { table.convertRowIndexToModel(it) } ?: return
        val newRow = row + direction
        if (newRow !in 0 until model.rowCount) return
        model.exchangeRows(row, newRow)
        selectModelRow(newRow)
    }

    private fun selectModelRow(row: Int) {
        val table = customRegexTable ?: return
        val viewRow = table.convertRowIndexToView(row)
        if (viewRow >= 0) {
            table.selectionModel.setSelectionInterval(viewRow, viewRow)
        }
    }

    private fun getCustomRegexRulesFromTable(): MutableList<CustomEvaluateExpressionBlockRule> {
        customRegexTable?.stopEditing()
        return customRegexTableModel
            ?.items
            ?.map { it.copyForPersistence() }
            ?.filter { it.pattern.isNotBlank() || it.reason.isNotBlank() || it.enabled }
            ?.toMutableList()
            ?: mutableListOf()
    }

    companion object {
        private const val HTML_WIDTH = 620

        const val CUSTOM_REGEX_HELP_HTML =
            "<html><body width='$HTML_WIDTH'>" +
                "<b>Additional blocked regex patterns:</b> matched after comments and string literals are removed. " +
                "They apply in Default blocklist and Read-only modes, never in Unrestricted mode. " +
                "Rules add to the built-in restrictions; they do not replace them." +
                "</body></html>"

        private const val RESTRICTED_CATEGORIES_HTML =
            "<html><body width='$HTML_WIDTH'>" +
                "<b>Built-in restricted categories:</b> process execution, JVM termination, filesystem access, " +
                "network access, reflection/access bypass, native code loading, and environment/system property access." +
                "</body></html>"

        fun modeExplanationHtml(mode: EvaluateExpressionSafetyMode): String {
            return "<html><body width='$HTML_WIDTH'><b>${mode}:</b> ${mode.explanation}</body></html>"
        }
    }
}

import re
with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

restore_code = """        // Interactive Command Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(inputBarBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                color = Color(0xFF6366F1),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = {
                    Text(
                        "Enter command...",
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input"),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = inputTextColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrect = false,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank()) {
                            onSendCommand(commandInput)
                            commandInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = if (isDark) Color(0xFF222630) else Color(0xFF94A3B8),
                    focusedContainerColor = inputFieldBg,
                    unfocusedContainerColor = inputFieldBg
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        onSendCommand(commandInput)
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("btn_send_terminal_command")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = if (commandInput.isNotBlank()) Color(0xFF6366F1) else Color(0xFF475569),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

"""

target = "        // Contextual Touch Control Strip for P0.3"

text = text.replace(target, restore_code + target)

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)

print("Restored!")

"""
Optional MCP (Model Context Protocol) server for ``bdc-calendars``.

This subpackage is not imported by :mod:`bdc_calendars` itself, so the base
package stays dependency-free; it (and the ``mcp`` SDK it wraps) is only
needed to run the ``bdc-calendars-mcp`` console script, installed via::

    pip install "bdc-calendars[mcp]"

See :mod:`bdc_calendars.mcp.server` for the tool definitions.
"""
